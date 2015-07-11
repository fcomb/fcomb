package io.fcomb.api.services

import io.fcomb.models._
import io.fcomb.validations
import akka.util.ByteString
import akka.http.scaladsl.model._, ContentTypes.`application/json`
import akka.stream.scaladsl.Source
import akka.stream.Materializer
import scala.concurrent.{ ExecutionContext, Future }
import spray.json._
import scalaz._, Scalaz._

trait ServiceResponse {
  def apply(contentType: ContentType, entity: Source[ByteString, Any]): Future[(Any, StatusCode)]
}

trait ResponseEntity

trait ServiceFlow[T] {
  val body: T
}

case class JsonServiceFlow(body: JsValue) extends ServiceFlow[JsValue]

object JsonServiceFlow {
  val contentType = `application/json`

  def apply(entity: Source[ByteString, Any])(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer
  ): Future[String \/ JsonServiceFlow] =
    entity
      .runFold(ByteString.empty)(_ ++ _)
      .map { data => JsonServiceFlow(data.utf8String.parseJson).right }
}

object ServiceFlow {
  def apply(
    contentType: ContentType,
    entity:      Source[ByteString, Any]
  )(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer
  ): Future[String \/ ServiceFlow[_]] =
    contentType match {
      case JsonServiceFlow.contentType => // TODO: add json as default
        JsonServiceFlow(entity)
    }
}

sealed trait ResponseMarshaller {
  def apply[T](v: T)(implicit jw: JsonWriter[T]): String
}

object JsonResponseMarshaller extends ResponseMarshaller {
  def apply[T](v: T)(implicit jw: JsonWriter[T]) =
    jw.write(v).toString
}

trait ApiService {
  def validationErrors(errors: (String, String)*): ValidationErrors =
    ValidationErrors(errors.map {
      case (k, v) => (k, NonEmptyList(v))
    }.toMap)

  import io.fcomb.json._
  implicitly[JsonWriter[ValidationErrors]]

  def toResponse[T, E <: ApiServiceResponse](value: T)(implicit f: T => E) =
    f(value)

  def handleRequest[T <: ApiServiceRequest](
    f: (T, ResponseMarshaller) => Future[(_, StatusCode)]
  )(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer,
    tm:           Manifest[T],
    jr:           JsonReader[T]
  ): ServiceResponse =
    new ServiceResponse {
      def apply(contentType: ContentType, entity: Source[ByteString, Any]) = {
        ServiceFlow(contentType, entity).flatMap {
          case \/-(JsonServiceFlow(json)) =>
            scala.util.Try(json.convertTo[T]) match {
              case scala.util.Success(res) =>
                f(res, JsonResponseMarshaller)
              case scala.util.Failure(e) =>
                Future.successful((
                  JsonResponseMarshaller(validationErrors("json" -> e.getMessage())),
                  StatusCodes.UnprocessableEntity
                ))
            }
          case -\/(e) =>
            println(s"e: $e")
            throw new Exception(e.toString) // TODO: handle exceptions within content type
        }
      }
    }

  def requestAs[T <: ApiServiceRequest, E <: ApiServiceResponse](
    f: T => Future[E]
  )(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer,
    tm:           Manifest[T],
    jr:           JsonReader[T],
    jw:           JsonWriter[E]
  ): ServiceResponse =
    handleRequest[T] { (req, marshaller) =>
      f(req).map { res => (marshaller(res), StatusCodes.OK) }
    }

  def requestAsWithValidation[T <: ApiServiceRequest, E <: ApiServiceResponse](
    f: T => validations.FutureValidationMapResult[E]
  )(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer,
    tm:           Manifest[T],
    jr:           JsonReader[T],
    jw:           JsonWriter[E]
  ): ServiceResponse =
    handleRequest[T] { (req, marshaller) =>
      f(req).map {
        case Success(res) =>
          (marshaller(res), StatusCodes.OK)
        case Failure(e) =>
          (marshaller(ValidationErrors(e)), StatusCodes.UnprocessableEntity)
      }
    }
}
