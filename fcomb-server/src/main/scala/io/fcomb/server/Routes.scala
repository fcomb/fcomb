package io.fcomb.server

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import io.fcomb.server.api._
import io.fcomb.server.headers._

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
    // format: OFF
    respondWithDefaultHeaders(defaultHeaders) {
      pathPrefix(apiVersion) {
        pathPrefix(UserHandler.pathPrefix) {
          pathPrefix("sign_up") {
            pathEndOrSingleSlash {
              post(UserHandler.signUp)
            }
          } ~
          pathPrefix("me") {
            pathEndOrSingleSlash {
              get(UserHandler.me) /*~
              put(UserHandler.updateProfile)
            } ~
            pathPrefix("password") {
              pathEndOrSingleSlash {
                put(UserHandler.changePassword)
              }
            }
          } ~
          pathPrefix("reset_password") {
            pathEndOrSingleSlash {
              post(UserHandler.resetPassword) ~
              put(UserHandler.setPassword) */
            }
          }
        } ~
        pathPrefix("sessions") {
          pathEndOrSingleSlash {
            post(SessionHandler.create) ~
            delete(SessionHandler.destroy)
          }
        } ~
        pathPrefix("ping") {
          pathEndOrSingleSlash {
            complete(pongJsonResponse)
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
