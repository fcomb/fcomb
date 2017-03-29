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

package io.fcomb.server.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.config.Settings
import io.fcomb.json.models.errors.Formats._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.common.Slug
import io.fcomb.models.errors.Errors
import io.fcomb.persist.UsersRepo
import io.fcomb.rpc.helpers.UserHelpers
import io.fcomb.rpc.{UserCreateRequest, UserSignUpRequest, UserUpdateRequest}
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.PaginationDirectives._
import io.fcomb.server.PathMatchers._

object UsersHandler {
  def index()(implicit config: ApiHandlerConfig) =
    authorizeAdminUser { user =>
      extractPagination { pg =>
        import config._
        onSuccess(UsersRepo.paginate(pg)) { p =>
          completePagination(UsersRepo.label, p)
        }
      }
    }

  def create()(implicit config: ApiHandlerConfig) =
    authorizeAdminUser { user =>
      entity(as[UserCreateRequest]) { req =>
        import config._
        completeAsCreated(UsersRepo.create(req).map(_.map(UserHelpers.response)))
      }
    }

  def update(slug: Slug)(implicit config: ApiHandlerConfig) =
    authorizeAdminUser { user =>
      entity(as[UserUpdateRequest]) { req =>
        import config._
        completeAsAccepted(UsersRepo.update(slug, user, req).map(_.map(UserHelpers.response)))
      }
    }

  def show(slug: Slug)(implicit config: ApiHandlerConfig) =
    authorizeAdminUser { user =>
      import config._
      completeOrNotFound(UsersRepo.find(slug).map(_.map(UserHelpers.response)))
    }

  def destroy(slug: Slug)(implicit config: ApiHandlerConfig) =
    authorizeAdminUser { user =>
      import config._
      completeAsAccepted(UsersRepo.destroy(slug, user))
    }

  def signUp()(implicit config: ApiHandlerConfig) =
    if (config.settings.security.openSignUp) {
      entity(as[UserSignUpRequest]) { req =>
        import config._
        onSuccess(UsersRepo.create(req)) {
          case Validated.Valid(_)      => completeWithStatus(StatusCodes.Created)
          case Validated.Invalid(errs) => completeErrors(errs)
        }
      }
    } else
      complete((StatusCodes.Forbidden, Errors.from(Errors.registrationIsDisabled)))

  def routes()(implicit config: ApiHandlerConfig): Route =
    // format: off
    pathPrefix("users") {
      pathEnd {
        get(index()) ~
        post(create())
      } ~
      pathPrefix(SlugPath) { slug =>
        pathEnd {
          get(show(slug)) ~
          put(update(slug)) ~
          delete(destroy(slug))
        } ~
        users.RepositoriesHandler.routes(slug)
      } ~
      path("sign_up")(post(signUp()))
    }
    // format: on
}
