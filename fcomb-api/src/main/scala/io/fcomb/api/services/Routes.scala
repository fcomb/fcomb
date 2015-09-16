package io.fcomb.api.services

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import io.fcomb.api.services.ServiceRoute.Implicits._

object Routes {
  private val pongJsonResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(
      ContentTypes.`application/json`,
      """{"pong":true}"""
    )
  )

  // format: OFF
  def apply()(implicit sys: ActorSystem, mat: Materializer): Route = {
    import sys.dispatcher

    pathPrefix("v1") {
      // pathPrefix("combs") {
      //   pathEndOrSingleSlash {
      //     post(CombService.create)
      //   } ~
      //   pathPrefix(LongNumber) { id: Long =>
      //     pathEndOrSingleSlash {
      //       get(CombService.show(id)) ~
      //       put(CombService.update(id)) ~
      //       delete(CombService.destroy(id))
      //     } ~
      //     pathPrefix("methods") {
      //       pathEndOrSingleSlash {
      //         post(CombMethodService.create(id))
      //       }
      //     }
      //   }
      // } ~
      pathPrefix("users") {
        path("sign_up") {
          post(UserService.signUp)
        } // ~
      //   pathPrefix("me") {
      //     pathEndOrSingleSlash {
      //       get(UserService.me) ~
      //       put(UserService.updateProfile)
      //     } ~
      //     path("password") {
      //       put(UserService.changePassword)
      //     }
      //   } ~
      //   path("reset_password") {
      //     post(UserService.resetPassword) ~
      //     put(UserService.setPassword)
      //   }
      // } ~
      // pathPrefix("sessions") {
      //   pathEndOrSingleSlash {
      //     post(SessionService.create) ~
      //     delete(SessionService.destroy)
      //   }
      } ~
      path("ping") {
        get(complete(pongJsonResponse))
      }
    }
  }
  // format: ON
}
