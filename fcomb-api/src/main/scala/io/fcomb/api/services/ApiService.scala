package io.fcomb.api.services

import io.fcomb.models._
import akka.util.ByteString
import akka.http.scaladsl.model._, ContentTypes.`application/json`
import akka.stream.scaladsl.Source
import akka.stream.Materializer
import scala.concurrent.{ ExecutionContext, Future }
import argonaut._, Argonaut._
import scalaz._, Scalaz._

trait ServiceResponse {
  def apply(contentType: ContentType, entity: Source[ByteString, Any]): Future[Any]
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
      case JsonServiceFlow.contentType =>
        JsonServiceFlow(entity)
    }
}

trait ApiService {
  def requestAs[T <: ApiServiceRequest](
    f: T => ApiServiceResponse
  )(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer,
    m:            Manifest[T],
    dj:           DecodeJson[T] // TODO
  ): ServiceResponse =
    new ServiceResponse {
      def apply(contentType: ContentType, entity: Source[ByteString, Any]) = {
        ServiceFlow(contentType, entity).map {
          case \/-(JsonServiceFlow(json)) =>
            json.as[T].map(f)
          case -\/(e) =>
            println(s"e: $e")
            throw new Exception(e.toString)
        }
      }
    }
}
