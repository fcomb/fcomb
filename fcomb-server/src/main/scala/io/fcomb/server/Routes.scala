/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.server

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.fcomb.models.docker.distribution.ImageKey
import io.fcomb.server.api._
import io.fcomb.server.headers._

object Routes {
  private val pongJsonResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(
      ContentTypes.`application/json`,
      """{"pong":true}"""
    )
  )

  def apply(): Route = {
    // format: OFF
    redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
      respondWithDefaultHeaders(defaultHeaders) {
        pathPrefix(apiVersion) {
          pathPrefix(RepositoriesHandler.servicePath) {
            pathPrefix(IntNumber) { id =>
              RepositoriesHandler.routes(ImageKey.Id(id))
            } ~
            pathPrefix(Segments(2)) { xs =>
              val name = xs.mkString("/")
              RepositoriesHandler.routes(ImageKey.Name(name))
            }
          } ~
          pathPrefix(UserHandler.servicePath) {
            pathEnd {
              get(UserHandler.current)
            } ~
            pathPrefix(user.RepositoriesHandler.servicePath) {
              pathEnd {
                get(user.RepositoriesHandler.index) ~
                post(user.RepositoriesHandler.create)
              }
            }
          } ~
          pathPrefix(UsersHandler.servicePath) {
            path("sign_up") {
              post(UsersHandler.signUp)
            }
          } ~
          path("sessions") {
            post(SessionsHandler.create) ~
            delete(SessionsHandler.destroy)
          } ~
          path("ping") {
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
