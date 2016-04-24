package io.fcomb.api.services

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.headers.{Authorization, `Content-Disposition`, ContentDispositionTypes, GenericHttpCredentials, Location, `X-Forwarded-For`, `Remote-Address`}
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, Sink, FileIO}
import akka.util.ByteString
import io.circe.{Error ⇒ CirceError, _}
import io.circe.jawn._
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
import cats.data.{Xor, Validated}
import scala.util.Try
import spray.json._
import java.io.File
import java.net.{InetAddress, UnknownHostException}

sealed trait RequestBody[R] {
  val body: Option[R]
}

case class JsonBody(body: Option[JsValue]) extends RequestBody[JsValue]

// case class JsonBody2(body: Option[Json]) extends RequestBody[Json]

sealed trait Marshaller {
  def serialize[T](res: T)(implicit m: Manifest[T], jw: JsonWriter[T]): ByteString

  def deserialize(body: Source[ByteString, Any])(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ): Future[Xor[Throwable, RequestBody[_]]]
}

object JsonMarshaller extends Marshaller {
  def serialize[T](res: T)(implicit m: Manifest[T], jw: JsonWriter[T]) =
    ByteString(res.toJson.compactPrint)

  def deserialize(body: Source[ByteString, Any])(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ): Future[Xor[Throwable, RequestBody[_]]] =
    body
      .runFold(ByteString.empty)(_ ++ _)
      .map { data ⇒
        if (data.isEmpty) Xor.Right(JsonBody(None))
        else Xor.fromTry(Try(data.utf8String.parseJson))
          .map(res ⇒ JsonBody(Some(res)))
      }
}

// object Json2Marshaller extends Marshaller {
//   def serialize[T](res: T)(
//     implicit
//     m:   Manifest[T],
//     enc: Encoder[T]
//   ): ByteString =
//     ???
//   // ByteString(res.toJson.compactPrint))

//   def deserialize(body: Source[ByteString, Any])(
//     implicit
//     ec:  ExecutionContext,
//     mat: Materializer
//   ): Future[Xor[ParsingFailure, JsonBody2]] =
//     body
//       .runFold(ByteString.empty)(_ ++ _)
//       .map { data ⇒
//         if (data.isEmpty) Xor.Right(JsonBody2(None))
//         else parse(data.utf8String).map(res ⇒ JsonBody2(Some(res)))
//       }
// }

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
    source:        Source[ByteString, Any],
    statusCode:    StatusCode,
    contentType:   ContentType,
    headers:       List[HttpHeader]        = List.empty,
    contentLength: Option[Long]            = None
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
    case CompleteSourceResult(s, status, ct, headers, contentLength) ⇒
      val entity = contentLength match {
        case Some(length) ⇒ HttpEntity(ct, length, s)
        case None         ⇒ HttpEntity(ct, s)
      }
      rCtx.complete(HttpResponse(
        status = status,
        headers = headers,
        entity = entity
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
    headers:     List[HttpHeader]
  ): ServiceResult =
    CompleteResult(bs, statusCode, contentType, headers)

  def complete(
    f:           Future[ByteString],
    statusCode:  StatusCode,
    contentType: ContentType,
    headers:     List[HttpHeader]
  )(implicit ec: ExecutionContext): ServiceResult =
    CompleteFutureResult(f.map(complete(_, statusCode, contentType, headers)))

  def complete(
    source:        Source[ByteString, Any],
    statusCode:    StatusCode,
    contentType:   ContentType,
    headers:       List[HttpHeader],
    contentLength: Option[Long]            = None
  ): ServiceResult =
    CompleteSourceResult(source, statusCode, contentType, headers, contentLength)

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
  def mapThrowable(e: Throwable) = {
    e.printStackTrace() // TODO: debug only
    e match {
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
      case (e, status) ⇒
        complete(FailureResponse.fromException(e), status)
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
  ): Future[Xor[Throwable, Option[T]]] =
    ctx.requestBody().map(_.flatMap {
      case JsonBody(Some(json)) ⇒
        Xor.fromTry(Try(jr.read(json))).map(Some(_))
      case _ ⇒ Xor.Right(None)
    })

  def flatResult(res: ServiceResult)(
    implicit
    ec: ExecutionContext
  ): Future[ServiceResult] = res match {
    case CompleteFutureResult(f) ⇒ f.flatMap(flatResult)
    case res                     ⇒ FastFuture.successful(res)
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
      case Xor.Right(res) ⇒ f(res)
      case Xor.Left(e)    ⇒ completeThrowable(e)
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
      case _         ⇒ completeException(JsonBodyCantBeEmpty)(StatusCodes.BadRequest)
    }

  def completeExceptions(errors: Seq[DtCemException])(statusCode: StatusCode)(
    implicit
    ctx: ServiceContext
  ) =
    complete(FailureResponse.fromExceptions(errors), statusCode)

  def completeException(error: DtCemException)(statusCode: StatusCode)(
    implicit
    ctx: ServiceContext
  ) =
    complete(FailureResponse.fromException(error), statusCode)

  def completeNotFound()(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    completeException(RecordNotFoundException)(StatusCodes.NotFound)

  def complete[T](res: T, statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    m:   Manifest[T],
    jw:  JsonWriter[T]
  ): ServiceResult =
    complete(ctx.marshaller.serialize(res), statusCode, ctx.contentType, List.empty)

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

  def completeValidation[T](res: Validated[ValidationErrors, T], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    m:   Manifest[T],
    jw:  JsonWriter[T]
  ): ServiceResult = res match {
    case Validated.Valid(s)   ⇒ complete(s, statusCode)
    case Validated.Invalid(e) ⇒ complete(e)
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

  def completeValidationAsCreated(res: Validated[ValidationErrors, _], uri: Uri)(
    implicit
    ctx: ServiceContext
  ): ServiceResult = res match {
    case Validated.Valid(s)   ⇒ completeAsCreated(uri)
    case Validated.Invalid(e) ⇒ complete(e)
  }

  def completeValidationAsCreated(f: Future[Validated[ValidationErrors, _]], uri: Uri)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult =
    complete(f.map(completeValidationAsCreated(_, uri)))

  def completeValidationWithPkAsCreated(
    res:       Validated[ValidationErrors, _ <: ModelWithPk],
    uriPrefix: Uri
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult =
    res match {
      case Validated.Valid(res) ⇒
        completeAsCreated(s"$apiPrefix/$uriPrefix/${res.getId}")
      case Validated.Invalid(e) ⇒ complete(e)
    }

  def completeValidationWithPkAsCreated(
    f:         Future[Validated[ValidationErrors, _ <: ModelWithPk]],
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

  def completeValidation[T](f: Future[Validated[ValidationErrors, T]], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext,
    m:   Manifest[T],
    jw:  JsonWriter[T]
  ): ServiceResult =
    complete(f.map(completeValidation(_, statusCode)))

  def completeWithoutResult(
    statusCode: StatusCode,
    headers:    List[HttpHeader] = List.empty
  )(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    CompleteWithoutResult(statusCode, ctx.contentType, headers)

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

  def completeValidationWithoutContent(res: Validated[ValidationErrors, _])(
    implicit
    ctx: ServiceContext
  ): ServiceResult = res match {
    case Validated.Valid(s)   ⇒ completeWithoutContent()
    case Validated.Invalid(e) ⇒ complete(e)
  }

  def completeValidationWithoutContent(
    f: Future[Validated[ValidationErrors, _]]
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ): ServiceResult =
    complete(f.map(completeValidationWithoutContent))

  def completeValidationWithoutResult(
    res:        Validated[ValidationErrors, _],
    statusCode: StatusCode,
    headers:    List[HttpHeader]               = List.empty
  )(
    implicit
    ctx: ServiceContext
  ): ServiceResult = res match {
    case Validated.Valid(s)   ⇒ completeWithoutResult(statusCode, headers)
    case Validated.Invalid(e) ⇒ complete(e)
  }

  def getAuthToken()(implicit ctx: ServiceContext): Option[String] = {
    val r = ctx.requestContext.request
    val credentials = r.header[Authorization].map(_.credentials)
    val authParameter = r.uri.query().get("access_token")
    (credentials, authParameter) match {
      case (Some(GenericHttpCredentials("Token", token, _)), _) ⇒ Some(token)
      case (_, token) ⇒ token
    }
  }

  def mapAccessException(e: DtCemException)(
    implicit
    ctx: ServiceContext
  ) =
    Future.successful(completeException(e)(StatusCodes.Unauthorized))

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
        case None     ⇒ completeException(CantExtractClientIpAddress)(StatusCodes.BadRequest)
      }
  }
}
