package io.fcomb.api.services

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import io.fcomb.api.services.ServiceRoute.Implicits._

object Routes {
  val apiVersion = "v1"

  private val pongJsonResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(
      ContentTypes.`application/json`,
      """{"pong":true}"""
    )
  )

  def apply()(implicit sys: ActorSystem, mat: Materializer): Route = {
    import sys.dispatcher

    // format: OFF
    pathPrefix(apiVersion) {
      // pathPrefix("combs") {
      //   pathEndOrSingleSlash {
      //     post(comb.CombService.create)
      //   } ~
      //   pathPrefix(LongNumber) { id: Long =>
      //     pathEndOrSingleSlash {
      //       get(comb.CombService.show(id)) ~
      //       put(comb.CombService.update(id)) ~
      //       delete(comb.CombService.destroy(id))
      //     } ~
      //     pathPrefix("methods") {
      //       pathEndOrSingleSlash {
      //         post(comb.CombMethodService.create(id))
      //       }
      //     }
      //   }
      // } ~
      pathPrefix(node.NodeService.pathPrefix) {
        pathPrefix("join") {
          pathEndOrSingleSlash {
            post(node.NodeService.join)
          }
        }
      } ~
      pathPrefix("account") {
        pathPrefix(UserTokenService.pathPrefix) {
          pathEndOrSingleSlash {
            get(UserTokenService.index)
          }
        }
      } ~
      pathPrefix(UserService.pathPrefix) {
        pathPrefix("sign_up") {
          pathEndOrSingleSlash {
            post(UserService.signUp)
          }
        } ~
        pathPrefix("me") {
          pathEndOrSingleSlash {
            get(UserService.me) ~
            put(UserService.updateProfile)
          } ~
          pathPrefix("password") {
            pathEndOrSingleSlash {
              put(UserService.changePassword)
            }
          }
        } ~
        pathPrefix("reset_password") {
          pathEndOrSingleSlash {
            post(UserService.resetPassword) ~
            put(UserService.setPassword)
          }
        }
      } ~
      pathPrefix("sessions") {
        pathEndOrSingleSlash {
          post(SessionService.create) ~
          delete(SessionService.destroy)
        }
      } ~
      pathPrefix("ping") {
        pathEndOrSingleSlash {
          get(complete(pongJsonResponse))
        }
      }
    }
    // format: ON
  }
}
