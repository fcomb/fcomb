package io.fcomb.api.services

import io.fcomb.models._
import io.fcomb.persist
import io.fcomb.validations
import io.fcomb.utils._
import akka.util.ByteString
import akka.http.scaladsl.model._, ContentTypes.`application/json`
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.stream.scaladsl.Source
import akka.stream.Materializer
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import spray.json._
import scalaz._, Scalaz._

sealed trait RequestBody[R] {
  val body: Option[R]
}

case class JsonBody(body: Option[JsValue]) extends RequestBody[JsValue]

sealed trait Marshaller {
  def serialize[T](res: T): ByteString

  def deserialize(body: Source[ByteString, Any])(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ): Future[Throwable \/ RequestBody[_]]
}

case object JsonMarshaller extends Marshaller {
  def serialize[T](res: T) =
    ByteString("test")

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

sealed trait ServiceResult {
  val statusCode: StatusCode
  val contentType: ContentType
}

object ServiceResult {
  case class CompleteResult(
    response: ByteString,
    statusCode: StatusCode,
    contentType: ContentType
  ) extends ServiceResult

  case class CompleteFutureResult(
    response: Future[ByteString],
    statusCode: StatusCode,
    contentType: ContentType
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
    case _ => JsonMarshaller // default marshaller
  }

  def requestBody()(implicit ec: ExecutionContext, mat: Materializer) =
    marshaller.deserialize(requestContext.request.entity.dataBytes)
}

trait ServiceMethod {
  def apply(ctx: ServiceContext): ServiceResult
}

object ServiceRoute {
  object Implicits {
    implicit def serviceMethod2Route(
      method: ServiceMethod
    )(
      implicit
      ec: ExecutionContext
    ): Route =
      { rCtx: RequestContext =>
        val ctx = new ServiceContext {
          val requestContext = rCtx
          val requestContentType = rCtx.request.entity.contentType()
        }
        method(ctx) match {
          case CompleteResult(res, status, ct) =>
            rCtx.complete(HttpResponse(
              status = status,
              entity = HttpEntity(ct, res)
            ))
          case CompleteFutureResult(f, status, ct) =>
            rCtx.complete(f.map(res => HttpResponse(
              status = status,
              entity = HttpEntity(ct, res)
            )))
          case CompleteSourceResult(s, status, ct) =>
            rCtx.complete(HttpResponse(
              status = status,
              entity = HttpEntity.CloseDelimited(ct, s)
            ))
        }
      }
  }
}

trait Service {
  def action(f: ServiceContext => ServiceResult) =
    new ServiceMethod {
      def apply(ctx: ServiceContext) = f(ctx)
    }

  def requestBodyAs[T]()(
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

  def complete(
    bs: String,
    statusCode: StatusCode,
    contentType: ContentType
  ): ServiceResult =
    CompleteResult(ByteString(bs), statusCode, contentType)

  def complete(
    f: Future[String],
    statusCode: StatusCode,
    contentType: ContentType
  )(
    implicit
    ec: ExecutionContext
  ): ServiceResult =
    CompleteFutureResult(f.map(ByteString(_)), statusCode, contentType)

  def complete(
    s: Source[ByteString, Any],
    statusCode: StatusCode,
    contentType: ContentType
  ): ServiceResult =
    CompleteSourceResult(s, statusCode, contentType)

  def validationErrors(errors: (String, String)*) =
    ValidationErrorsResponse(errors.map {
      case (k, v) => (k, List(v))
    }.toMap)

  import io.fcomb.json._
  implicitly[JsonWriter[ValidationErrorsResponse]]

  def toResponse[T, E <: ModelServiceResponse](value: T)(implicit f: T => E) =
    f(value)

  // def handleRequest[T <: ModelServiceRequest](
  //   f: (T, ResponseMarshaller) => Future[(_, StatusCode)]
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer,
  //   tm:           Manifest[T],
  //   jr:           JsonReader[T]
  // ): ServiceResponse =
  //   new ServiceResponse {
  //     def apply(
  //       contentType:   ContentType,
  //       entity:        Source[ByteString, Any],
  //       requestParams: => RequestParams
  //     ) = {
  //       ServiceFlow(contentType, entity).flatMap {
  //         case \/-((ct, JsonServiceFlow(json))) =>
  //           scala.util.Try(json.convertTo[T]) match {
  //             case scala.util.Success(res) =>
  //               f(res, JsonResponseMarshaller).map {
  //                 case (body, statusCode) =>
  //                   (ct, body, statusCode)
  //               }
  //             case scala.util.Failure(e) =>
  //               Future.successful((
  //                 ct,
  //                 JsonResponseMarshaller(validationErrors("json" -> e.getMessage())),
  //                 StatusCodes.UnprocessableEntity
  //               ))
  //           }
  //         case -\/(e) =>
  //           println(s"e: $e")
  //           throw new Exception(e.toString) // TODO: handle exceptions within content type
  //       }
  //     }
  //   }

  // def requestAs[T <: ModelServiceRequest, E <: ModelServiceResponse](
  //   f: T => Future[E]
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer,
  //   tm:           Manifest[T],
  //   jr:           JsonReader[T],
  //   jw:           JsonWriter[E]
  // ): ServiceResponse =
  //   handleRequest[T] { (req, marshaller) =>
  //     f(req).map { res => (marshaller(res), StatusCodes.OK) }
  //   }

  // def requestAsWithValidation[T <: ModelServiceRequest, E <: ModelServiceResponse](
  //   f: T => Future[validations.ValidationResult[E]]
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer,
  //   tm:           Manifest[T],
  //   jr:           JsonReader[T],
  //   jw:           JsonWriter[E]
  // ): ServiceResponse =
  //   handleRequest[T] { (req, marshaller) =>
  //     f(req).map {
  //       case Success(res) =>
  //         res match {
  //           case NoContentResponse() =>
  //             ("", StatusCodes.NoContent)
  //           case _ =>
  //             (marshaller(res), StatusCodes.OK)
  //         }
  //       case Failure(e) =>
  //         (
  //           marshaller(ValidationErrorsResponse(e)),
  //           StatusCodes.UnprocessableEntity
  //         )
  //     }
  //   }

  // def renderAs[T <: ModelServiceResponse](
  //   contentType: ContentType,
  //   entity:      T,
  //   statusCode:  StatusCode
  // )(
  //   implicit
  //   jw: JsonWriter[T]
  // ) =
  //   ServiceFlow.render(contentType, entity) match {
  //     case (ct, body) => (ct, body, statusCode)
  //   }

  // def response[T <: ModelServiceResponse](
  //   res: => T
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer,
  //   tm:           Manifest[T],
  //   jw:           JsonWriter[T]
  // ): ServiceResponse =
  //   new ServiceResponse {
  //     def apply(
  //       contentType:   ContentType,
  //       entity:        Source[ByteString, Any],
  //       requestParams: => RequestParams
  //     ) =
  //       Future.successful(renderAs(contentType, res, StatusCodes.OK))
  //   }

  // def responseAs[M, T <: ModelServiceResponse](
  //   res: => M
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer,
  //   tm:           Manifest[T],
  //   jw:           JsonWriter[T],
  //   f:            M => T
  // ): ServiceResponse =
  //   response(f(res))

  // def unauthorizedError(contentType: ContentType, message: String) =
  //   renderAs(
  //     contentType,
  //     validationErrors("auth" -> message),
  //     StatusCodes.Unauthorized
  //   )

  // def authorization(
  //   f: User => ServiceResponse
  // )(
  //   implicit
  //   ec:           ExecutionContext,
  //   mat: Materializer
  // ): ServiceResponse =
  //   new ServiceResponse {
  //     def apply(
  //       contentType:   ContentType,
  //       entity:        Source[ByteString, Any],
  //       requestParams: => RequestParams
  //     ) = requestParams match {
  //       case HttpRequestParams(request) =>
  //         def g(token: String) =
  //           persist.Session.findById(token).flatMap {
  //             case Some(user) => f(user)(contentType, entity, requestParams)
  //             case None => Future.successful {
  //               unauthorizedError(contentType, "Invalid token")
  //             }
  //           }

  //         val authHeader = request.headers.collectFirst {
  //           case a: Authorization ⇒ a
  //         }

  //         val authParameter = request.uri.query.get("auth_token")
  //         (authHeader, authParameter) match {
  //           case (Some(token), _) =>
  //             token.value.split(' ') match {
  //               case Array("Token" | "token", token) => g(token)
  //               case _ => Future.successful {
  //                 unauthorizedError(contentType, "Expected format 'Token <token>'")
  //               }
  //             }
  //           case (_, Some(token)) => g(token)
  //           case _ => Future.successful {
  //             unauthorizedError(contentType, "Expected 'Authorization' header with token or GET 'auth_token' parameter")
  //           }
  //         }
  //     }
  //   }
}
