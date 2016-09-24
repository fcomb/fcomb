package io.fcomb.application.server

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import io.circe.{DecodingFailure, ParsingFailure, HistoryOp}
import io.fcomb.json.models.errors.Formats.encodeErrors
import io.fcomb.models.errors.{Errors, Error}
import io.fcomb.server.CirceSupport._
import io.fcomb.server.CommonDirectives._

class HttpApiService(routes: Route)(implicit sys: ActorSystem, mat: Materializer)
    extends LazyLogging {
  import sys.dispatcher

  private def mapThrowable(e: Throwable) =
    e match {
      case _: ParsingException | _: Unmarshaller.UnsupportedContentTypeException =>
        (StatusCodes.UnprocessableEntity, Errors.deserialization(e.getMessage))
      case f: DecodingFailure =>
        val path = HistoryOp.opsToPath(f.history) match {
          case s if s.startsWith(".") => s.tail
          case s                      => s
        }
        (StatusCodes.UnprocessableEntity, Errors.deserialization(f.message, Some(path)))
      case f: ParsingFailure =>
        (StatusCodes.UnprocessableEntity, Errors.deserialization(f.message))
      case _ =>
        (StatusCodes.InternalServerError, Errors.unknown(e.getMessage))
    }

  private def completeError(status: StatusCode, error: Error) =
    complete((status, Errors.from(error)))

  private def handleException(e: Throwable) =
    (completeError _).tupled(mapThrowable(e))

  private def handleRejection(r: Rejection) = r match {
    case _ => completeError(StatusCodes.BadRequest, Errors.unknown(r.toString))
  }

  private val exceptionHandler = ExceptionHandler {
    case e =>
      logger.error(e.getMessage(), e.getCause())
      handleException(e)
  }

  private val rejectionHandler = RejectionHandler
    .newBuilder()
    .handleAll[AuthorizationFailedRejection.type] { _ =>
      complete(HttpResponse(StatusCodes.Unauthorized))
    }
    .handle {
      case r =>
        logger.error(r.toString)
        handleRejection(r)
    }
    .handleNotFound(completeNotFound())
    .result

  private val handler =
    handleRejections(rejectionHandler) {
      handleExceptions(exceptionHandler)(routes)
    }

  def bind(port: Int, interface: String) =
    Http().bindAndHandle(handler, interface, port)
}

object HttpApiService {
  def start(port: Int, interface: String, routes: Route)(implicit sys: ActorSystem,
                                                         mat: Materializer) =
    new HttpApiService(routes).bind(port, interface)
}
