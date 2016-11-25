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
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.util.FastFuture._
import cats.data.Validated
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.models.errors.Formats._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.common.Slug
import io.fcomb.models.errors.Errors
import io.fcomb.persist.UsersRepo
import io.fcomb.rpc.helpers.UserHelpers
import io.fcomb.rpc.ResponseModelWithPk._
import io.fcomb.rpc.{UserCreateRequest, UserSignUpRequest, UserUpdateRequest}
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.PaginationDirectives._
import io.fcomb.server.Path
import io.fcomb.utils.Config

object UsersHandler {
  val handlerPath = "users"

  def index =
    extractExecutionContext { implicit ec =>
      authorizeAdminUser { user =>
        extractPagination { pg =>
          onSuccess(UsersRepo.paginate(pg)) { p =>
            completePagination(UsersRepo.label, p)
          }
        }
      }
    }

  def create =
    extractExecutionContext { implicit ec =>
      authorizeAdminUser { user =>
        entity(as[UserCreateRequest]) { req =>
          completeAsCreated(UsersRepo.create(req).fast.map(_.map(UserHelpers.response)))
        }
      }
    }

  def update(slug: Slug) =
    extractExecutionContext { implicit ec =>
      authorizeAdminUser { user =>
        entity(as[UserUpdateRequest]) { req =>
          completeAsAccepted(
            UsersRepo.update(slug, user, req).fast.map(_.map(UserHelpers.response)))
        }
      }
    }

  def show(slug: Slug) =
    extractExecutionContext { implicit ec =>
      authorizeAdminUser { user =>
        completeOrNotFound(UsersRepo.find(slug).fast.map(_.map(UserHelpers.response)))
      }
    }

  def destroy(slug: Slug) =
    extractExecutionContext { implicit ec =>
      authorizeAdminUser { user =>
        completeOrNoContent(UsersRepo.destroy(slug, user))
      }
    }

  def signUp =
    if (Config.security.isOpenSignUp) {
      extractExecutionContext { implicit ec =>
        entity(as[UserSignUpRequest]) { req =>
          onSuccess(UsersRepo.create(req)) {
            case Validated.Valid(_)      => completeWithStatus(StatusCodes.Created)
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    } else
      complete((StatusCodes.Forbidden, Errors.from(Errors.registrationIsDisabled)))

  val routes: Route =
    // format: OFF
    pathPrefix(handlerPath) {
      pathEnd {
        get(index) ~
        post(create)
      } ~
      pathPrefix(Path.Slug) { slug =>
        pathEnd {
          get(show(slug)) ~
          put(update(slug)) ~
          delete(destroy(slug))
        } ~
        users.RepositoriesHandler.routes(slug)
      } ~
      path("sign_up")(post(signUp))
    }
    // format: ON
}
