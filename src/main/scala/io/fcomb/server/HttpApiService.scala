package io.fcomb.server

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import io.fcomb.json.errors._
import io.fcomb.models.errors._
import org.slf4j.LoggerFactory
import spray.json._

class HttpApiService(routes: Route)(
    implicit
    sys: ActorSystem,
    mat: Materializer
) {
  private val logger = LoggerFactory.getLogger(this.getClass)

  private def mapThrowable(e: Throwable) = {
    e.printStackTrace() // TODO: debug only
    e match {
      case _: DeserializationException | _: ParsingException |
        _: Unmarshaller.UnsupportedContentTypeException ⇒
        (
          InternalException(e.getMessage),
          StatusCodes.UnprocessableEntity
        )
      case _ ⇒
        (
          InternalException(e.getMessage),
          StatusCodes.InternalServerError
        )
    }
  }

  private def errorResponse[T <: DtCemException](
    error:  T,
    status: StatusCode
  ) = {
    val e = FailureResponse.fromException(error)
    complete(
      HttpResponse(
        status = status,
        entity = HttpEntity(
          ContentTypes.`application/json`,
          e.toJson.compactPrint
        )
      )
    )
  }

  private def handleException(e: Throwable) =
    mapThrowable(e) match {
      case (e, status) ⇒ errorResponse(e, status)
    }

  private def handleRejection(r: Rejection) = r match {
    case _ ⇒
      errorResponse(
        InternalException(r.toString),
        StatusCodes.BadRequest
      )
  }

  val handler = {
    val exceptionHandler = ExceptionHandler {
      case e ⇒
        logger.error(e.getMessage(), e.getCause())
        handleException(e)
    }
    val rejectionHandler = RejectionHandler
      .newBuilder()
      .handle {
        case r ⇒
          logger.error(r.toString, r.toString)
          handleRejection(r)
      }
      .handleNotFound {
        errorResponse(ResourceNotFoundException, StatusCodes.NotFound)
      }
      .result

    // handleRejections(rejectionHandler) {
    //   handleExceptions(exceptionHandler)(routes)
    // }
    routes
  }

  def bind(port: Int, interface: String) =
    Http().bindAndHandle(handler, interface, port)
}

object HttpApiService {
  def start(port: Int, interface: String, routes: Route)(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) =
    new HttpApiService(routes).bind(port, interface)
}
