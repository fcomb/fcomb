package io.fcomb.api.services

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import io.fcomb.api.services.headers._

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
    respondWithDefaultHeaders(defaultHeaders) {
      pathPrefix(apiVersion) {
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
    }
    // format: ON
  }

  private val defaultHeaders = List(
    `X-Content-Type-Options`("nosniff"),
    `X-Frame-Options`("sameorigin"),
    `X-XSS-Protection`("1; mode=block")
  // Strict-Transport-Security: max-age=31536000; includeSubDomains
  // Content-Security-Policy: default-src 'none'; script-src 'self'; connect-src 'self'; img-src 'self'; style-src 'self';
  )
}
