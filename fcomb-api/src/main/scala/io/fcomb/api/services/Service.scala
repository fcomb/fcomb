package io.fcomb.api.services

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.headers.{Authorization, `Content-Disposition`, ContentDispositionTypes, Location, `X-Forwarded-For`, `Remote-Address`}
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, Sink, FileIO}
import akka.util.ByteString
import io.fcomb.json._
import io.fcomb.json.errors._
import io.fcomb.models._
import io.fcomb.models.errors._
import io.fcomb.persist
import io.fcomb.utils._
import io.fcomb.validations.ValidationErrors
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import scalaz._
import scalaz.Scalaz._
import spray.json._
import java.io.File
import java.net.{InetAddress, UnknownHostException}

sealed trait RequestBody[R] {
  val body: Option[R]
}

case class JsonBody(body: Option[JsValue]) extends RequestBody[JsValue]

sealed trait Marshaller {
  def serialize[T](res: T)(implicit m: Manifest[T], jw: JsonWriter[T]): ByteString

  def deserialize(body: Source[ByteString, Any])(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ): Future[Throwable \/ RequestBody[_]]
}

case object JsonMarshaller extends Marshaller {
  def serialize[T](res: T)(implicit m: Manifest[T], jw: JsonWriter[T]) =
    ByteString(res.toJson.compactPrint)

  def deserialize(body: Source[ByteString, Any])(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ): Future[Throwable \/ RequestBody[_]] =
    body
      .runFold(ByteString.empty)(_ ++ _)
      .map { data ⇒
        if (data.isEmpty) {
          JsonBody(None).right[Throwable]
        }
        else {
          tryE(data.utf8String.parseJson)
            .map(res ⇒ JsonBody(Some(res)))
        }
      }
}

sealed trait ServiceResult

object ServiceResult {
  sealed trait CompleteResultItem extends ServiceResult {
    val statusCode: StatusCode
    val contentType: ContentType
    val headers: List[HttpHeader]
  }

  case class CompleteWithoutResult(
    statusCode:  StatusCode,
    contentType: ContentType,
    headers:     List[HttpHeader] = List.empty
  ) extends CompleteResultItem

  case class CompleteResult(
    response:    ByteString,
    statusCode:  StatusCode,
    contentType: ContentType,
    headers:     List[HttpHeader] = List.empty
  ) extends CompleteResultItem

  case class CompleteFutureResult(
    f: Future[ServiceResult]
  ) extends ServiceResult

  case class CompleteSourceResult(
    source:      Source[ByteString, Any],
    statusCode:  StatusCode,
    contentType: ContentType,
    headers:     List[HttpHeader]        = List.empty
  ) extends CompleteResultItem
}
import ServiceResult._

trait ServiceContext {
  val requestContentType: ContentType
  val requestContext: RequestContext

  val contentType: ContentType = requestContentType match {
    case _ ⇒ `application/json`
  }

  val marshaller: Marshaller = contentType match {
    case _ ⇒ JsonMarshaller
  }

  def requestBody()(implicit ec: ExecutionContext, mat: Materializer) =
    marshaller.deserialize(requestContext.request.entity.dataBytes)
}

trait ServiceMethod {
  def apply(ctx: ServiceContext): ServiceResult
}

object ServiceRoute {
  def serviceResultToRoute(
    rCtx: RequestContext,
    res:  ServiceResult
  )(
    implicit
    ec: ExecutionContext
  ): Future[RouteResult] = res match {
    case CompleteResult(res, status, ct, headers) ⇒
      rCtx.complete(HttpResponse(
        status = status,
        headers = headers,
        entity = HttpEntity(ct, res)
      ))
    case CompleteSourceResult(s, status, ct, headers) ⇒
      rCtx.complete(HttpResponse(
        status = status,
        headers = headers,
        entity = HttpEntity.CloseDelimited(ct, s)
      ))
    case CompleteWithoutResult(status, ct, headers) ⇒
      rCtx.complete(HttpResponse(
        status = status,
        headers = headers,
        entity = HttpEntity.empty(ct)
      ))
    case CompleteFutureResult(f) ⇒
      f.flatMap(serviceResultToRoute(rCtx, _))
  }

  def serviceMethodToRoute(method: ServiceMethod)(
    implicit
    ec: ExecutionContext
  ): Route = { rCtx: RequestContext ⇒
    val ctx = new ServiceContext {
      val requestContext = rCtx
      val requestContentType = rCtx.request.entity.contentType
    }
    serviceResultToRoute(rCtx, method(ctx))
  }

  object Implicits {
    implicit def serviceMethod2RouteImplicit(
      method: ServiceMethod
    )(
      implicit
      ec: ExecutionContext
    ): Route =
      serviceMethodToRoute(method)
  }
}

trait CompleteResultMethods {
  def complete(
    bs:          ByteString,
    statusCode:  StatusCode,
    contentType: ContentType,
    headers:     List[HttpHeader] = List.empty
  ): ServiceResult =
    CompleteResult(bs, statusCode, contentType, headers)

  def complete(
    f:           Future[ByteString],
    statusCode:  StatusCode,
    contentType: ContentType
  )(implicit ec: ExecutionContext): ServiceResult =
    CompleteFutureResult(f.map(complete(_, statusCode, contentType)))

  def complete(
    source:      Source[ByteString, Any],
    statusCode:  StatusCode,
    contentType: ContentType
  ): ServiceResult =
    CompleteSourceResult(source, statusCode, contentType)

  def completeFile(
    source: Source[ByteString, Any],
    name:   Option[String]
  ): ServiceResult = {
    val headers = name match {
      case Some(filename) if filename.nonEmpty ⇒
        List(`Content-Disposition`(
          ContentDispositionTypes.attachment,
          Map("filename" → filename)
        ))
      case _ ⇒ List.empty
    }
    CompleteSourceResult(
      source,
      StatusCodes.OK,
      ContentTypes.`application/octet-stream`,
      headers
    )
  }
}

trait ServiceLogging {
  lazy val logger = LoggerFactory.getLogger(getClass)
}

trait ServiceExceptionMethods {
  def mapThrowable(e: Throwable) = e match {
    case _: DeserializationException |
      _: ParsingException |
      _: Unmarshaller.UnsupportedContentTypeException ⇒
      (
        InternalException(e.getMessage),
        StatusCodes.UnprocessableEntity
      )
    case _ ⇒ (
      InternalException(e.getMessage),
      StatusCodes.InternalServerError
    )
  }
}

trait Service extends CompleteResultMethods with ServiceExceptionMethods with ServiceLogging {
  val apiPrefix = s"/${Routes.apiVersion}"

  def action(f: ServiceContext ⇒ ServiceResult) =
    new ServiceMethod {
      def apply(ctx: ServiceContext) =
        try {
          f(ctx)
        }
        catch {
          case e: Throwable ⇒
            logger.error(e.getMessage, e.getCause)
            completeThrowable(e)(ctx)
        }
    }

  def completeThrowable(e: Throwable)(implicit ctx: ServiceContext) =
    mapThrowable(e) match {
      case (e, status) ⇒ complete(e.toErrorMessage(), status)
    }

  def recoverThrowable(f: Future[ServiceResult])(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): Future[ServiceResult] = f.recover {
    case e: Throwable ⇒
      logger.error(e.getMessage, e.getCause)
      completeThrowable(e)
  }

  def requestBodyTryAs[T]()(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    mat: Materializer,
    tm:  Manifest[T],
    jr:  JsonReader[T]
  ): Future[Throwable \/ Option[T]] =
    ctx.requestBody().map(_.flatMap {
      case JsonBody(Some(json)) ⇒
        tryE(jr.read(json)).map(Some(_))
      case _ ⇒ None.right[Throwable]
    })

  def flatResult(res: ServiceResult)(
    implicit
    ec: ExecutionContext
  ): Future[ServiceResult] = res match {
    case CompleteFutureResult(f) ⇒ f.flatMap(flatResult)
    case res                     ⇒ Future.successful(res)
  }

  def complete(f: Future[ServiceResult])(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult =
    CompleteFutureResult(recoverThrowable(f.flatMap(flatResult)))

  def requestBodyAsOpt[T](f: Option[T] ⇒ ServiceResult)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    mat: Materializer,
    tm:  Manifest[T],
    jr:  JsonReader[T]
  ): ServiceResult =
    complete(requestBodyTryAs[T]().map {
      case \/-(res) ⇒ f(res)
      case -\/(e)   ⇒ completeThrowable(e)
    })

  def requestBodyAs[T](f: T ⇒ ServiceResult)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    mat: Materializer,
    tm:  Manifest[T],
    jr:  JsonReader[T]
  ): ServiceResult =
    requestBodyAsOpt[T] {
      case Some(res) ⇒ f(res)
      case _         ⇒ completeError(JsonBodyCantBeEmpty)(StatusCodes.BadRequest)
    }

  def completeErrors(errors: Seq[DtCemException])(statusCode: StatusCode)(
    implicit
    ctx: ServiceContext
  ) =
    complete(FailureResponse.fromExceptions(errors), statusCode)

  def completeError(error: DtCemException)(statusCode: StatusCode)(
    implicit
    ctx: ServiceContext
  ) =
    complete(FailureResponse.fromException(error), statusCode)

  def completeNotFound()(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    completeError(RecordNotFoundException)(StatusCodes.NotFound)

  def complete[T](res: T, statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    m:   Manifest[T],
    jw:  JsonWriter[T]
  ): ServiceResult =
    complete(ctx.marshaller.serialize(res), statusCode, ctx.contentType)

  def complete[T](f: Future[T], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    m:   Manifest[T],
    jw:  JsonWriter[T]
  ): ServiceResult =
    complete(f.map(complete(_, statusCode)))

  def completeItems[T](f: Future[Seq[T]], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    m:   Manifest[T],
    jw:  JsonWriter[MultipleDataResponse[T]]
  ): ServiceResult =
    complete(f.map(items ⇒ complete(MultipleDataResponse[T](items, None), statusCode)))

  def completeValidation[T](res: Validation[ValidationErrors, T], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    m:   Manifest[T],
    jw:  JsonWriter[T]
  ): ServiceResult = res match {
    case Success(s) ⇒ complete(s, statusCode)
    case Failure(e) ⇒ complete(e)
  }

  def completeAsCreated(uri: Uri, headers: List[HttpHeader] = List.empty)(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    CompleteWithoutResult(
      StatusCodes.Created,
      ctx.contentType,
      Location(uri) :: headers
    )

  def completeValidationAsCreated(res: Validation[ValidationErrors, _], uri: Uri)(
    implicit
    ctx: ServiceContext
  ): ServiceResult = res match {
    case Success(s) ⇒ completeAsCreated(uri)
    case Failure(e) ⇒ complete(e)
  }

  def completeValidationAsCreated(f: Future[Validation[ValidationErrors, _]], uri: Uri)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult =
    complete(f.map(completeValidationAsCreated(_, uri)))

  def completeValidationWithPkAsCreated(
    res:       Validation[ValidationErrors, _ <: ModelWithPk[_]],
    uriPrefix: Uri
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult =
    res match {
      case Success(res) ⇒
        completeAsCreated(s"$apiPrefix/$uriPrefix/${res.getId}")
      case Failure(e) ⇒ complete(e)
    }

  def completeValidationWithPkAsCreated(
    f:         Future[Validation[ValidationErrors, _ <: ModelWithPk[_]]],
    uriPrefix: Uri
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult =
    complete(f.map(completeValidationWithPkAsCreated(_, uriPrefix)))

  def complete(errors: ValidationErrors)(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    complete(FailureResponse.fromExceptions(errors), StatusCodes.UnprocessableEntity)

  def completeValidation[T](f: Future[Validation[ValidationErrors, T]], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    m:   Manifest[T],
    jw:  JsonWriter[T]
  ): ServiceResult =
    complete(f.map(completeValidation(_, statusCode)))

  def completeWithoutResult(statusCode: StatusCode)(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    CompleteWithoutResult(statusCode, ctx.contentType)

  def completeWithoutContent()(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    completeWithoutResult(StatusCodes.NoContent)

  def completeWithoutContent(f: Future[_])(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult =
    complete(f.map(_ ⇒ completeWithoutContent))

  def completeOrNotFound[T](opt: Option[T], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    m:   Manifest[T],
    jw:  JsonWriter[T]
  ): ServiceResult = opt match {
    case Some(item) ⇒ complete(item, statusCode)
    case None       ⇒ completeNotFound
  }

  def completeOrNotFound[T](f: Future[Option[T]], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    m:   Manifest[T],
    jw:  JsonWriter[T]
  ): ServiceResult =
    complete(f.map(opt ⇒ completeOrNotFound(opt, statusCode)))

  def completeValidationWithoutContent(
    res: Validation[ValidationErrors, _]
  )(
    implicit
    ctx: ServiceContext
  ): ServiceResult = res match {
    case Success(s) ⇒ completeWithoutContent()
    case Failure(e) ⇒ complete(e)
  }

  def completeValidationWithoutContent(
    f: Future[Validation[ValidationErrors, _]]
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult =
    complete(f.map(completeValidationWithoutContent))

  def getAuthToken()(implicit ctx: ServiceContext): Option[String] = {
    val r = ctx.requestContext.request
    val authHeader = r.headers.collectFirst {
      case a: Authorization ⇒ a
    }
    val authParameter = r.uri.query().get("access_token")
    (authHeader, authParameter) match {
      case (Some(token), _) ⇒
        val s = token.value.split(' ')
        if (s.head.toLowerCase == "token")
          Some(s.last.take(256)) // restrict token length
        else None
      case (_, token @ Some(_)) ⇒ token
      case _                    ⇒ None
    }
  }

  def mapAccessException(e: DtCemException)(
    implicit
    ctx: ServiceContext
  ) =
    Future.successful(completeError(e)(StatusCodes.Unauthorized))

  def authorizeUser(
    f: User ⇒ ServiceResult
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult = {
    complete(getAuthToken match {
      case Some(token) if token.startsWith(persist.Session.prefix) ⇒
        persist.Session.findById(token).flatMap {
          case Some(item) ⇒ flatResult(f(item))
          case None       ⇒ mapAccessException(InvalidAuthorizationToken)
        }
      case _ ⇒ mapAccessException(ExpectedAuthorizationToken)
    })
  }

  def authorizeUserByToken(role: TokenRole.TokenRole)(
    f: User ⇒ ServiceResult
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult = {
    complete(getAuthToken match {
      case Some(token) if token.startsWith(persist.UserToken.prefix) ⇒
        persist.User.findByToken(token, role).flatMap {
          case Some(item) ⇒ flatResult(f(item))
          case None       ⇒ mapAccessException(InvalidAuthorizationToken)
        }
      case _ ⇒ mapAccessException(ExpectedAuthorizationToken)
    })
  }

  def requestAsBodyParts(
    f: Source[Multipart.FormData.BodyPart, Any] ⇒ Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    complete(Unmarshal(ctx.requestContext.request.entity)
      .to[Multipart.FormData]
      .flatMap(body ⇒ f(body.parts)))

  def requestAsBodyPart(
    f: Multipart.FormData.BodyPart ⇒ Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    requestAsBodyParts(_.runWith(Sink.head).flatMap(f))

  def requestAsFileParts(
    f: Source[Multipart.FormData.BodyPart, Any] ⇒ Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    requestAsBodyParts { parts ⇒
      f(parts.filter(_.additionalDispositionParams.contains("filename")))
    }

  def requestAsFilePart(
    f: Multipart.FormData.BodyPart ⇒ Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    requestAsFileParts(_.runWith(Sink.head).flatMap(f))

  def makeTemporaryFile(extension: Option[String] = None) = {
    val prefix = Random.random.alphanumeric.take(15).mkString
    val filename = s"/tmp/file_${prefix}${extension.getOrElse("")}"
    val file = new File(filename)
    file.deleteOnExit()
    file
  }

  def requestAsFile(f: (File, Option[String], ContentType) ⇒ ServiceResult)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    requestAsFilePart { part ⇒
      val file = makeTemporaryFile(part.filename.flatMap { n ⇒
        n.split('.').tail.lastOption.map(e ⇒ s".$e")
      })
      part.entity.dataBytes.runWith(FileIO.toFile(file))
        .map(_ ⇒ f(file, part.filename, part.entity.contentType))
    }

  def extractClientIp(f: InetAddress ⇒ ServiceResult)(
    implicit
    ctx: ServiceContext,
    mat: Materializer
  ): ServiceResult = {
    def g(pf: PartialFunction[HttpHeader, Option[InetAddress]]): Option[InetAddress] =
      ctx.requestContext.request.headers.collectFirst(pf).flatMap(identity)

    (g { case `X-Forwarded-For`(Seq(address, _*)) ⇒ address.toIP.map(_.ip) })
      .orElse(g { case `Remote-Address`(address) ⇒ address.toIP.map(_.ip) })
      .orElse(g {
        case h if h.is("x-real-ip") || h.is("cf-connecting-ip") ⇒
          try Some(InetAddress.getByName(h.value))
          catch { case _: UnknownHostException ⇒ None }
      }) match {
        case Some(ip) ⇒ f(ip)
        case None     ⇒ completeError(CantExtractClientIpAddress)(StatusCodes.BadRequest)
      }
  }
}
