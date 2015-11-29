package io.fcomb.api.services

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.headers.{Authorization, `Content-Disposition`, ContentDispositionTypes}
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.{Source, Sink}
import akka.stream.io.SynchronousFileSink
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

sealed trait RequestBody[R] {
  val body: Option[R]
}

case class JsonBody(body: Option[JsValue]) extends RequestBody[JsValue]

sealed trait Marshaller {
  def serialize[T](res: T)(implicit m: Manifest[T], jw: JsonWriter[T]): ByteString

  def deserialize(body: Source[ByteString, Any])(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ): Future[Throwable \/ RequestBody[_]]
}

case object JsonMarshaller extends Marshaller {
  def serialize[T](res: T)(implicit m: Manifest[T], jw: JsonWriter[T]) =
    ByteString(res.toJson.compactPrint)

  def deserialize(body: Source[ByteString, Any])(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ): Future[Throwable \/ RequestBody[_]] =
    body
      .runFold(ByteString.empty)(_ ++ _)
      .map { data =>
        if (data.isEmpty) {
          JsonBody(None).right[Throwable]
        } else {
          tryE(data.utf8String.parseJson)
            .map(res => JsonBody(Some(res)))
        }
      }
}

sealed trait ServiceResult

object ServiceResult {
  case class CompleteWithoutResult(
    statusCode: StatusCode,
    contentType: ContentType
  ) extends ServiceResult

  case class CompleteResult(
    response: ByteString,
    statusCode: StatusCode,
    contentType: ContentType
  ) extends ServiceResult

  case class CompleteFutureResult(
    f: Future[ServiceResult]
  ) extends ServiceResult

  case class CompleteSourceResult(
    s: Source[ByteString, Any],
    statusCode: StatusCode,
    contentType: ContentType
  ) extends ServiceResult

  case class CompleteFileResult(
    s: Source[ByteString, Any],
    filename: Option[String]
  ) extends ServiceResult
}
import ServiceResult._

trait ServiceContext {
  val requestContentType: ContentType
  val requestContext: RequestContext

  val contentType: ContentType = requestContentType match {
    case _ => `application/json`
  }

  val marshaller: Marshaller = contentType match {
    case _ => JsonMarshaller
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
    res: ServiceResult
  )(
    implicit
    ec: ExecutionContext
  ): Future[RouteResult] = res match {
    case CompleteResult(res, status, ct) =>
      rCtx.complete(HttpResponse(
        status = status,
        entity = HttpEntity(ct, res)
      ))
    case CompleteSourceResult(s, status, ct) =>
      rCtx.complete(HttpResponse(
        status = status,
        entity = HttpEntity.CloseDelimited(ct, s)
      ))
    case CompleteWithoutResult(status, ct) =>
      rCtx.complete(HttpResponse(
        status = status,
        entity = HttpEntity.empty(ct)
      ))
    case CompleteFutureResult(f) =>
      f.flatMap(serviceResultToRoute(rCtx, _))
    case CompleteFileResult(s, name) =>
      val headers = name match {
        case Some(filename) if filename.nonEmpty =>
          List(`Content-Disposition`(
            ContentDispositionTypes.attachment,
            Map("filename" -> filename)
          ))
        case _ => List.empty
      }
      rCtx.complete(HttpResponse(
        status = StatusCodes.OK,
        entity = HttpEntity(ContentTypes.`application/octet-stream`, s),
        headers = headers
      ))
  }

  def serviceMethodToRoute(method: ServiceMethod)(
    implicit
    ec: ExecutionContext
  ): Route = { rCtx: RequestContext =>
    val ctx = new ServiceContext {
      val requestContext = rCtx
      val requestContentType = rCtx.request.entity.contentType()
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
    bs: ByteString,
    statusCode: StatusCode,
    contentType: ContentType
  ): ServiceResult =
    CompleteResult(bs, statusCode, contentType)

  def complete(
    f: Future[ByteString],
    statusCode: StatusCode,
    contentType: ContentType
  )(implicit ec: ExecutionContext): ServiceResult =
    CompleteFutureResult(f.map { bs =>
      CompleteResult(bs, statusCode, contentType)
    })

  def complete(
    s: Source[ByteString, Any],
    statusCode: StatusCode,
    contentType: ContentType
  ): ServiceResult =
    CompleteSourceResult(s, statusCode, contentType)

  def completeFile(
    s: Source[ByteString, Any],
    filename: Option[String]
  ): ServiceResult =
    CompleteFileResult(s, filename)
}

trait ServiceLogging {
  lazy val logger = LoggerFactory.getLogger(getClass)
}

trait ServiceExceptionMethods {
  def mapThrowable(e: Throwable) = e match {
    case _: DeserializationException |
      _: ParsingException |
      _: Unmarshaller.UnsupportedContentTypeException =>
      (
        InternalException(e.getMessage),
        StatusCodes.UnprocessableEntity
      )
    case _ => (
      InternalException(e.getMessage),
      StatusCodes.InternalServerError
    )
  }
}

trait Service extends CompleteResultMethods with ServiceExceptionMethods with ServiceLogging {
  def action(f: ServiceContext => ServiceResult) =
    new ServiceMethod {
      def apply(ctx: ServiceContext) =
        try {
          f(ctx)
        } catch {
          case e: Throwable =>
            logger.error(e.getMessage, e.getCause)
            completeThrowable(e)(ctx)
        }
    }

  def completeThrowable(e: Throwable)(implicit ctx: ServiceContext) =
    mapThrowable(e) match {
      case (e, status) => complete(e.toErrorMessage(), status)
    }

  def recoverThrowable(f: Future[ServiceResult])(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext
  ): Future[ServiceResult] = f.recover {
    case e: Throwable =>
      logger.error(e.getMessage, e.getCause)
      completeThrowable(e)
  }

  def requestBodyTryAs[T]()(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    mat: Materializer,
    tm: Manifest[T],
    jr: JsonReader[T]
  ): Future[Throwable \/ Option[T]] =
    ctx.requestBody().map(_.flatMap {
      case JsonBody(Some(json)) =>
        tryE(jr.read(json)).map(Some(_))
      case _ => None.right[Throwable]
    })

  def flatResult(res: ServiceResult)(
    implicit
    ec: ExecutionContext
  ): Future[ServiceResult] = res match {
    case CompleteFutureResult(f) => f.flatMap(flatResult)
    case res => Future.successful(res)
  }

  def complete(f: Future[ServiceResult])(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext
  ): ServiceResult =
    CompleteFutureResult(recoverThrowable(f.flatMap(flatResult)))

  def requestBodyAs[T](f: T => ServiceResult)(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    mat: Materializer,
    tm: Manifest[T],
    jr: JsonReader[T]
  ): ServiceResult =
    complete(requestBodyTryAs[T]().map {
      case \/-(o) => o match {
        case Some(res) => f(res)
        case _ => completeError(JsonBodyCantBeEmpty)(StatusCodes.BadRequest)
      }
      case -\/(e) => completeThrowable(e)
    })

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
    m: Manifest[T],
    jw: JsonWriter[T]
  ): ServiceResult =
    complete(ctx.marshaller.serialize(res), statusCode, ctx.contentType)

  def complete[T](f: Future[T], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    m: Manifest[T],
    jw: JsonWriter[T]
  ): ServiceResult =
    complete(f.map(complete(_, statusCode)))

  def completeItems[T](f: Future[Seq[T]], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    m: Manifest[T],
    jw: JsonWriter[MultipleDataResponse[T]]
  ): ServiceResult =
    complete(f.map(items => complete(MultipleDataResponse[T](items, None), statusCode)))

  def completeValidation[T](res: Validation[ValidationErrors, T], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    m: Manifest[T],
    jw: JsonWriter[T]
  ): ServiceResult = res match {
    case Success(s) => complete(s, statusCode)
    case Failure(e) => complete(e)
  }

  def complete(errors: ValidationErrors)(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    complete(FailureResponse.fromExceptions(errors), StatusCodes.UnprocessableEntity)

  def completeValidation[T](f: Future[Validation[ValidationErrors, T]], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    m: Manifest[T],
    jw: JsonWriter[T]
  ): ServiceResult =
    complete(f.map(completeValidation(_, statusCode)))

  def completeWithoutContent(statusCode: StatusCode)(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    CompleteWithoutResult(statusCode, ctx.contentType)

  def completeWithoutContent(f: Future[_], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext
  ): ServiceResult =
    complete(f.map(_ => completeWithoutContent(statusCode)))

  def completeOrNotFound[T](opt: Option[T], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    m: Manifest[T],
    jw: JsonWriter[T]
  ): ServiceResult = opt match {
    case Some(item) => complete(item, statusCode)
    case None => completeNotFound
  }

  def completeOrNotFound[T](f: Future[Option[T]], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    m: Manifest[T],
    jw: JsonWriter[T]
  ): ServiceResult =
    complete(f.map(opt => completeOrNotFound(opt, statusCode)))

  def completeValidationWithoutContent(
    res: Validation[ValidationErrors, _],
    statusCode: StatusCode
  )(
    implicit
    ctx: ServiceContext
  ): ServiceResult = res match {
    case Success(s) => completeWithoutContent(statusCode)
    case Failure(e) => complete(e)
  }

  def completeValidationWithoutContent(
    f: Future[Validation[ValidationErrors, _]],
    statusCode: StatusCode
  )(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext
  ): ServiceResult =
    complete(f.map(completeValidationWithoutContent(_, statusCode)))

  def getAuthToken()(implicit ctx: ServiceContext): Option[String] = {
    val r = ctx.requestContext.request
    val authHeader = r.headers.collectFirst {
      case a: Authorization ⇒ a
    }
    val authParameter = r.uri.query().get("auth_token")
    (authHeader, authParameter) match {
      case (Some(token), _) =>
        val s = token.value.split(' ')
        if (s.head.toLowerCase == "token") Some(s.last)
        else None
      case (_, token @ Some(_)) => token
      case _ => None
    }
  }

  def authorizeUser(
    f: User => ServiceResult
  )(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext
  ): ServiceResult = {
    def mapException(e: DtCemException) =
      Future.successful(completeError(e)(StatusCodes.Unauthorized))

    complete(getAuthToken() match {
      case Some(token) =>
        persist.Session.findById(token).flatMap {
          case Some(item) => flatResult(f(item))
          case None => mapException(InvalidAuthorizationToken)
        }
      case _ => mapException(ExpectedAuthorizationToken)
    })
  }

  def requestAsBodyParts[T](
    f: Source[Multipart.FormData.BodyPart, Any] => Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    complete(Unmarshal(ctx.requestContext.request.entity)
      .to[Multipart.FormData]
      .flatMap(body => f(body.parts)))

  def requestAsBodyPart[T](
    f: Multipart.FormData.BodyPart => Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    requestAsBodyParts(_.runWith(Sink.head).flatMap(f))

  def requestAsFileParts[T](
    f: Source[Multipart.FormData.BodyPart, Any] => Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    requestAsBodyParts { parts =>
      f(parts.filter(_.additionalDispositionParams.contains("filename")))
    }

  def requestAsFilePart[T](
    f: Multipart.FormData.BodyPart => Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    requestAsFileParts(_.runWith(Sink.head).flatMap(f))

  def requestAsFile[T](f: (File, Option[String], ContentType) => ServiceResult)(
    implicit
    ctx: ServiceContext,
    ec: ExecutionContext,
    mat: Materializer
  ): ServiceResult =
    requestAsFilePart { part =>
      val extension = part.filename.flatMap { n =>
        n.split('.').tail.lastOption.map(e => s".$e")
      }.getOrElse("")
      val prefix = Random.random.alphanumeric.take(15).mkString
      val filename = s"/tmp/file_${prefix}$extension"
      val file = new File(filename)
      file.deleteOnExit()
      part.entity.dataBytes.runWith(SynchronousFileSink(file))
        .map(_ => f(file, part.filename, part.entity.contentType()))
    }
}
