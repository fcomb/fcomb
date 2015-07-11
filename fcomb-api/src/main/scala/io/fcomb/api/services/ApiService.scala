package io.fcomb.api.services

import io.fcomb.models._
import io.fcomb.validations
import akka.util.ByteString
import akka.http.scaladsl.model._, ContentTypes.`application/json`
import akka.stream.scaladsl.Source
import akka.stream.Materializer
import scala.concurrent.{ ExecutionContext, Future }
import argonaut._, Argonaut._
import scalaz._, Scalaz._

trait ServiceResponse {
  def apply(contentType: ContentType, entity: Source[ByteString, Any]): Future[(Any, StatusCode)]
}

trait ResponseEntity

trait ServiceFlow[T] {
  val body: T
}

case class JsonServiceFlow(body: Json) extends ServiceFlow[Json]

object JsonServiceFlow {
  val contentType = `application/json`

  def apply(entity: Source[ByteString, Any])(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer
  ): Future[String \/ JsonServiceFlow] =
    entity
      .runFold(ByteString.empty)(_ ++ _)
      .map(_.utf8String.parse.map(JsonServiceFlow(_)))
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
  def apply[T](v: T)(implicit ej: EncodeJson[T]): String
}

object JsonResponseMarshaller extends ResponseMarshaller {
  def apply[T](v: T)(implicit ej: EncodeJson[T]) =
    ej(v).toString
}

trait ApiService {
  def validationErrors(errors: (String, String)*): ValidationErrors =
    ValidationErrors(errors.map {
      case (k, v) => (k, NonEmptyList(v))
    }.toMap)

  import io.fcomb.json._
  implicitly[EncodeJson[ValidationErrors]]

  def handleRequest[T <: ApiServiceRequest](
    f: (T, ResponseMarshaller) => Future[(_, StatusCode)]
  )(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer,
    tm:           Manifest[T],
    dj:           DecodeJson[T]
  ): ServiceResponse =
    new ServiceResponse {
      def apply(contentType: ContentType, entity: Source[ByteString, Any]) = {
        ServiceFlow(contentType, entity).flatMap {
          case \/-(JsonServiceFlow(json)) =>
            json.as[T] match {
              case DecodeResult(\/-(res)) =>
                f(res, JsonResponseMarshaller)
              case DecodeResult(-\/((e, cursor))) =>
                val res: ValidationErrors = cursor.head match {
                  case Some(El(CursorOpDownField(field), _)) =>
                    validationErrors(field.toString -> s"empty or invalid: $e")
                  case _ =>
                    validationErrors("json" -> s"$e, $cursor")
                }
                Future.successful(
                  (JsonResponseMarshaller(res), StatusCodes.UnprocessableEntity)
                )
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
    dj:           DecodeJson[T],
    ej:           EncodeJson[E]
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
    dj:           DecodeJson[T],
    ej:           EncodeJson[E]
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
