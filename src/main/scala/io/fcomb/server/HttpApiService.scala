package io.fcomb.server

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller._
import akka.stream.Materializer
import io.fcomb.json.errors._
import io.fcomb.models.errors._
import org.slf4j.LoggerFactory
import spray.json._

class HttpApiService(routes: Route)(implicit sys: ActorSystem, mat: Materializer) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def jsonResponse[T <: ErrorResponse](
    e: T,
    status: StatusCode
  )(
    implicit
    m: Manifest[T],
    jw: JsonWriter[T]
  ) =
    complete(HttpResponse(
      status = status,
      entity = HttpEntity(
        ContentTypes.`application/json`,
        e.toJson.compactPrint
      )
    ))

  private def handleException(e: Throwable) = e match {
    case _: DeserializationException |
      _: ParsingException |
      _: UnsupportedContentTypeException =>
      jsonResponse(
        SingleFailureResponse(ErrorStatus.Internal, e.getMessage),
        StatusCodes.BadRequest
      )
    case _ => jsonResponse(
      SingleFailureResponse(ErrorStatus.Internal, e.getMessage),
      StatusCodes.InternalServerError
    )
  }

  private def handleRejection(r: Rejection) = r match {
    case _ => jsonResponse(
      SingleFailureResponse(ErrorStatus.Internal, r.toString),
      StatusCodes.BadRequest
    )
  }

  val handler = {
    val exceptionHandler = ExceptionHandler {
      case e =>
        logger.error(e.getMessage(), e.getCause())
        handleException(e)
    }
    val rejectionHandler = RejectionHandler.newBuilder()
      .handle {
        case r =>
          logger.error(r.toString, r.toString)
          handleRejection(r)
      }
      .handleNotFound {
        jsonResponse(resourceNotFound, StatusCodes.NotFound)
      }
      .result

    handleRejections(rejectionHandler) {
      handleExceptions(exceptionHandler)(routes)
    }
  }

  def bind(port: Int, interface: String) =
    Http().bindAndHandle(handler, interface, port)
}

object HttpApiService {
  def start(port: Int, interface: String, routes: Route)(
    implicit
    sys: ActorSystem, mat: Materializer
  ) =
    new HttpApiService(routes).bind(port, interface)
}
