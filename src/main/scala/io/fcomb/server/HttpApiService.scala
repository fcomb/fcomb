package io.fcomb.server

import io.fcomb.api.JsonErrors
import io.fcomb.api.services._
import akka.actor._
import akka.stream.ActorMaterializer
import akka.http.scaladsl.Http
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._, ContentTypes.`application/json`
import akka.stream.scaladsl.Sink
import com.typesafe.config.Config
import scala.language.{ postfixOps, implicitConversions }

class HttpApiService(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer) {
  implicit val executionContext = system.dispatcher

  val interface = config.getString("rest-api.interface")
  val port = config.getInt("rest-api.port")

  // val exceptionHandler = ExceptionHandler {
  //   case e => complete(JsonErrors.handleException(e))
  // }
  //
  // val rejectionHandler = RejectionHandler.newBuilder()
  //   .handle {
  //     case r => complete(JsonErrors.handleRejection(r))
  //   }
  //   .handleNotFound {
  //     complete(JsonErrors.resourceNotFound)
  //   }
  //   .result

  private val pongJsonResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(
      `application/json`,
      """{"pong":true}"""
    )
  )

  // TODO
  implicit def serviceResponse2akkaRoute(r: ServiceResponse): Route =
    { ctx: RequestContext =>
      val ct = ctx.request.entity.contentType()
      val res = r(ct, ctx.request.entity.dataBytes, HttpRequestParams(ctx.request)).map {
        case (ct, body, status) =>
          HttpResponse(
            status = status,
            entity = HttpEntity(ct, body.toString)
          )
      }
      ctx.complete(res)
    }

  // format: OFF
  val routes: Route =
    pathPrefix("v1") {
      pathPrefix("users") {
        path("sign_up") {
          post(UserService.signUp)
        } ~
        pathPrefix("me") {
          pathEndOrSingleSlash {
            get(UserService.me) ~
            put(UserService.updateProfile)
          } ~
          path("password") {
            put(UserService.changePassword)
          }
        } ~
        path("reset_password") {
          post(UserService.resetPassword) ~
          put(UserService.setPassword)
        }
      } ~
      pathPrefix("sessions") {
        pathEndOrSingleSlash {
          post(SessionService.create) ~
          delete(SessionService.destroy)
        }
      } ~
      path("ping") {
        get(complete(pongJsonResponse))
      }
    }
  // format: ON

  // val handler = handleRejections(rejectionHandler) {
  //   handleExceptions(exceptionHandler)(routes)
  // }

  def bind() = {
    // val flow = Route.handlerFlow(handler)
    val flow = Route.handlerFlow(routes)
    Http().bind(
      interface = interface,
      port = port
    ).to(Sink.foreach(_.handleWith(flow))).run()
  }
}

object HttpApiService {
  def start(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer) =
    new HttpApiService(config).bind()
}
