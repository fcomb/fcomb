package io.fcomb.server

import io.fcomb.api.JsonErrors
import io.fcomb.api.services._
import akka.actor._
import akka.stream.ActorFlowMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl.Sink
import com.typesafe.config.Config
import scala.language.postfixOps

class HttpApiService(config: Config)(implicit system: ActorSystem, materializer: ActorFlowMaterializer) {
  implicit val executionContext = system.dispatcher

  val interface = config.getString("rest-api.interface")
  val port = config.getInt("rest-api.port")

  val exceptionHandler = ExceptionHandler {
    case e => complete(JsonErrors.handleException(e))
  }

  val rejectionHandler = RejectionHandler.newBuilder()
    .handle {
      case r => complete(JsonErrors.handleRejection(r))
    }
    .handleNotFound {
      complete(JsonErrors.resourceNotFound)
    }
    .result

  // format: OFF
  val routes: Route =
    pathPrefix("v1") {
      pathPrefix("users") {
        pathEndOrSingleSlash {
          post(UserService.create)
        } /*~
        path("me") {
          get(UserService.me)
        }*/
      }
    }
  // format: ON

  val handler = handleRejections(rejectionHandler) {
    handleExceptions(exceptionHandler)(routes)
  }

  def bind() = {
    val flow = Route.handlerFlow(handler)
    Http().bind(
      interface = interface,
      port = port
    ).to(Sink.foreach(_.handleWith(flow))).run()
  }
}

object HttpApiService {
  def start(config: Config)(implicit system: ActorSystem, materializer: ActorFlowMaterializer) =
    new HttpApiService(config).bind()
}
