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

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import io.fcomb.server.api._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.headers._

object Api {
  def routes(implicit sys: ActorSystem, mat: Materializer): Route = {
    implicit val config = ApiHandlerConfig(sys, mat)

    // format: OFF
    pathPrefix(apiVersion) {
      redirectToNoTrailingSlashIfPresent(StatusCodes.MovedPermanently) {
        respondWithDefaultHeaders(defaultHeaders) {
          new RepositoriesHandler().routes ~
          new OrganizationsHandler().routes ~
          new UserHandler().routes ~
          new UsersHandler().routes ~
          new SessionsHandler().routes
        }
      }
    } ~
    path("health")(completeWithStatus(StatusCodes.OK))
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
