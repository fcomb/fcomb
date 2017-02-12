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

package io.fcomb.server.api.repository

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.models.acl.{Action, MemberKind}
import io.fcomb.models.common.Slug
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.rpc.acl.PermissionCreateRequest
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.PaginationDirectives._
import io.fcomb.server.PathMatchers._

object PermissionsHandler {
  def index(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      image(slug, user.getId(), Action.Manage) { image =>
        extractPagination { pg =>
          import config._
          onSuccess(PermissionsRepo.paginateByImageId(image, pg)) { p =>
            completePagination(PermissionsRepo.label, p)
          }
        }
      }
    }

  def upsert(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      image(slug, user.getId(), Action.Manage) { image =>
        entity(as[PermissionCreateRequest]) { req =>
          import config._
          onSuccess(PermissionsRepo.upsertByImage(image, req)) {
            case Validated.Valid(p)      => complete((StatusCodes.Accepted, p))
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  def destroy(slug: Slug, memberKind: MemberKind, memberSlug: Slug)(
      implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      image(slug, user.getId(), Action.Manage) { image =>
        import config._
        onSuccess(PermissionsRepo.destroyByImage(image, memberKind, memberSlug)) {
          case Validated.Valid(p)      => completeAccepted()
          case Validated.Invalid(errs) => completeErrors(errs)
        }
      }
    }

  def suggestions(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      parameter('q) { q =>
        image(slug, user.getId(), Action.Manage) { image =>
          import config._
          onSuccess(PermissionsRepo.findSuggestions(image, q))(completeData)
        }
      }
    }

  def routes(slug: Slug)(implicit config: ApiHandlerConfig): Route =
    // format: OFF
    pathPrefix("permissions") {
      pathEnd {
        get(index(slug)) ~
        put(upsert(slug))
      } ~
      path("suggestions" / "members")(get(suggestions(slug))) ~
      path(MemberKindPath / SlugPath) { (kind, member) =>
        delete(destroy(slug, kind, member))
      }
    }
    // format: ON
}
