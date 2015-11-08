package io.fcomb.api.services

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.server.{RequestContext, Route, RouteResult}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import akka.stream.scaladsl.Source
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
        SingleFailureResponse(ErrorStatus.Internal, e.getMessage),
        StatusCodes.UnprocessableEntity
      )
    case _ => (
      SingleFailureResponse(ErrorStatus.Internal, e.getMessage),
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
      case (e, status) => complete(e, status)
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
        case _ =>
          completeErrors("body" -> "can't be empty")(StatusCodes.BadRequest)
      }
      case -\/(e) => completeThrowable(e)
    })

  def completeErrors(errors: (String, String)*)(statusCode: StatusCode)(
    implicit
    ctx: ServiceContext
  ) =
    complete(validationErrors(errors: _*), statusCode)

  def validationErrors(errors: (String, String)*) =
    ValidationErrorsMap(errors.map {
      case (k, v) => (k, List(v))
    }.toMap)

  def complete[T](res: T, statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    m: Manifest[T],
    jw: JsonWriter[T]
  ): ServiceResult =
    complete(ctx.marshaller.serialize(res), statusCode, ctx.contentType)

  def completeValidation[T](res: Validation[ValidationErrors, T], statusCode: StatusCode)(
    implicit
    ctx: ServiceContext,
    m: Manifest[T],
    jw: JsonWriter[T]
  ): ServiceResult = res match {
    case Success(s) => complete(s, statusCode)
    case Failure(e) => complete(e)
  }

  def complete(e: ValidationErrors)(
    implicit
    ctx: ServiceContext
  ): ServiceResult =
    complete(ValidationErrorsMap(e), StatusCodes.UnprocessableEntity)

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
    def e(message: String) = Future.successful(
      completeErrors("auth" -> message)(StatusCodes.Unauthorized)
    )

    complete(getAuthToken() match {
      case Some(token) =>
        persist.Session.findById(token).flatMap {
          case Some(item) => flatResult(f(item))
          case None => e("invalid token")
        }
      case _ => e("expected 'Authorization' header with token or GET 'auth_token' parameter")
    })
  }
}
