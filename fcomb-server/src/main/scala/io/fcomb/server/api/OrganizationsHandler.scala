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

package io.fcomb.server.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.json.rpc.Formats._
import io.fcomb.persist.OrganizationsRepo
import io.fcomb.rpc.OrganizationCreateRequest
import io.fcomb.rpc.helpers.OrganizationHelpers
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.CirceSupport._
import scala.collection.immutable

object OrganizationsHandler {
  val servicePath = "organizations"

  lazy val fullPrefix = s"/$apiVersion/$servicePath/"

  def create = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        entity(as[OrganizationCreateRequest]) { req =>
          onSuccess(OrganizationsRepo.create(req, user.getId())) {
            case Validated.Valid(org) =>
              val uri     = fullPrefix + org.getId().toString
              val headers = immutable.Seq(Location(uri))
              val res     = OrganizationHelpers.responseFrom(org, isPublic = false)
              respondWithHeaders(headers) {
                complete((StatusCodes.Created, res))
              }
            case Validated.Invalid(e) =>
              ??? // TODO
          }
        }
      }
    }
  }

  def show(id: Int) = {
    extractExecutionContext { implicit ec =>
      tryAuthenticateUser { userOpt =>
        onSuccess(OrganizationsRepo.findById(id)) {
          case Some(org) =>
            // TODO: check user is organization admin
            val res = OrganizationHelpers.responseFrom(org, isPublic = userOpt.isEmpty)
            complete((StatusCodes.OK, res))
          case None => completeNotFound()
        }
      }
    }
  }

  val routes: Route = {
    // format: OFF
    pathPrefix(servicePath) {
      pathEnd {
        post(create)
      } ~
      pathPrefix(IntNumber) { id =>
        get(show(id))
      }
    }
    // format: ON
  }
}
