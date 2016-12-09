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

package io.fcomb.server.api.organization.group

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.rpc.Formats.{decodeMemberUserRequest, encodeUserProfileResponse}
import io.fcomb.models.common.Slug
import io.fcomb.persist.OrganizationGroupUsersRepo
import io.fcomb.rpc.MemberUserRequest
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.OrganizationGroupDirectives._
import io.fcomb.server.PaginationDirectives._
import io.fcomb.server.PathMatchers._

object MembersHandler {
  def index(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      groupBySlugWithAcl(slug, group, user.getId()) { group =>
        extractPagination { pg =>
          import config._
          onSuccess(OrganizationGroupUsersRepo.paginateByGroupId(group.getId(), pg)) { p =>
            completePagination(OrganizationGroupUsersRepo.label, p)
          }
        }
      }
    }

  def upsert(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      groupBySlugWithAcl(slug, group, user.getId()) { group =>
        entity(as[MemberUserRequest]) { req =>
          import config._
          onSuccess(OrganizationGroupUsersRepo.upsert(group.getId(), req)) {
            case Validated.Valid(p)      => completeAccepted()
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  def destroy(slug: Slug, group: Slug, memberSlug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      groupBySlugWithAcl(slug, group, user.getId()) { group =>
        import config._
        onSuccess(OrganizationGroupUsersRepo.destroy(group.getId(), user.getId(), memberSlug)) {
          case Validated.Valid(p)      => completeAccepted()
          case Validated.Invalid(errs) => completeErrors(errs)
        }
      }
    }

  def routes(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig): Route =
    // format: OFF
    pathPrefix("members") {
      pathEnd {
        get(index(slug, group)) ~
        put(upsert(slug, group))
      } ~
      path(SlugPath)(member => delete(destroy(slug, group, member)))
    }
    // format: ON
}
