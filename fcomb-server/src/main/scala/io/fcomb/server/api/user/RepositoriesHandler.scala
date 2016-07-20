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

package io.fcomb.server.api.user

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.server.CirceSupport._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.rpc.docker.distribution.ImageCreateRequest
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.PaginationDirectives._
import io.fcomb.server.api.apiVersion
import scala.collection.immutable

object RepositoriesHandler {
  val servicePath = "repositories"

  lazy val resourcePrefix = s"/$apiVersion/${RepositoriesHandler.servicePath}/"

  def index = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        extractPagination { pg =>
          val res = ImagesRepo.findAvailableByUserId(user.getId(), pg)
          onSuccess(res) { p =>
            completePagination(ImagesRepo.label, p)
          }
        }
      }
    }
  }

  def create = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        entity(as[ImageCreateRequest]) { req =>
          onSuccess(ImagesRepo.create(req, user)) {
            case Validated.Valid(image) =>
              val uri     = resourcePrefix + image.getId().toString
              val headers = immutable.Seq(Location(uri))
              val res     = ImageHelpers.responseFrom(image, Action.Manage)
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

  val routes: Route = {
    // format: OFF
    path(servicePath) {
      get(index) ~
      post(create)
    }
    // format: ON
  }
}
