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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.rpc.docker.distribution.ImageCreateRequest
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.server.api.apiVersion
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.PaginationDirectives._

object RepositoriesHandler {
  val handlerPath = "repositories"

  lazy val resourcePrefix = s"/$apiVersion/${RepositoriesHandler.handlerPath}/"

  def index =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        extractPagination { pg =>
          val res = ImagesRepo.paginateByUser(user.getId(), pg)
          onSuccess(res) { p =>
            completePagination(ImagesRepo.label, p)
          }
        }
      }
    }

  def available =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        extractPagination { pg =>
          val res = ImagesRepo.paginateAvailableByUserId(user.getId(), pg)
          onSuccess(res) { p =>
            completePagination(ImagesRepo.label, p)
          }
        }
      }
    }

  def create =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        entity(as[ImageCreateRequest]) { req =>
          onSuccess(ImagesRepo.create(req, user)) {
            case Validated.Valid(image) =>
              val res = ImageHelpers.response(image, Action.Manage)
              completeCreated(res, resourcePrefix)
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  val routes: Route = {
    // format: OFF
    pathPrefix(handlerPath) {
      pathEnd {
        get(index) ~
        post(create)
      } ~
      path("available")(get(available))
    }
    // format: ON
  }
}
