/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.rpc.docker.distribution.ImageCreateRequest
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.PaginationDirectives._

object RepositoriesHandler {
  def index()(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      extractPagination { pg =>
        import config._
        onSuccess(ImagesRepo.paginateByUser(user.getId(), pg))(
          completePagination(ImagesRepo.label, _))
      }
    }

  def available()(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      extractPagination { pg =>
        import config._
        onSuccess(ImagesRepo.paginateAvailableByUserId(user.getId(), pg))(
          completePagination(ImagesRepo.label, _))
      }
    }

  def create()(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      entity(as[ImageCreateRequest]) { req =>
        import config._
        onSuccess(ImagesRepo.create(req, user)) {
          case Validated.Valid(image) =>
            completeCreated(ImageHelpers.response(image, Action.Manage))
          case Validated.Invalid(errs) => completeErrors(errs)
        }
      }
    }

  def routes()(implicit config: ApiHandlerConfig): Route =
    // format: OFF
    pathPrefix("repositories") {
      pathEnd {
        get(index()) ~
        post(create())
      } ~
      path("available")(get(available()))
    }
    // format: ON
}
