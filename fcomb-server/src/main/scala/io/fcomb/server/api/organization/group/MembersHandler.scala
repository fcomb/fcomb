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
import io.fcomb.json.rpc.Formats.{decodeMemberUserRequest, encodeUserProfileResponse}
import io.fcomb.models.common.Slug
import io.fcomb.persist.OrganizationGroupUsersRepo
import io.fcomb.rpc.MemberUserRequest
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CirceSupport._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.OrganizationGroupDirectives._
import io.fcomb.server.PaginationDirectives._

object MembersHandler {
  val handlerPath = "members"

  def index(slug: Slug, groupSlug: Slug) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        groupBySlugWithAcl(slug, groupSlug, user.getId()) { group =>
          extractPagination { pg =>
            onSuccess(OrganizationGroupUsersRepo.paginateByGroupId(group.getId(), pg)) { p =>
              completePagination(OrganizationGroupUsersRepo.label, p)
            }
          }
        }
      }
    }

  def upsert(slug: Slug, groupSlug: Slug) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        groupBySlugWithAcl(slug, groupSlug, user.getId()) { group =>
          entity(as[MemberUserRequest]) { req =>
            onSuccess(OrganizationGroupUsersRepo.upsert(group.getId(), req)) {
              case Validated.Valid(p)      => completeAccepted()
              case Validated.Invalid(errs) => completeErrors(errs)
            }
          }
        }
      }
    }

  def destroy(slug: Slug, groupSlug: Slug, memberSlug: Slug) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        groupBySlugWithAcl(slug, groupSlug, user.getId()) { group =>
          onSuccess(OrganizationGroupUsersRepo.destroy(group.getId(), memberSlug)) {
            case Validated.Valid(p)      => completeAccepted()
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  def routes(slug: Slug, groupSlug: Slug): Route =
    // format: OFF
    pathPrefix(handlerPath) {
      pathEnd {
        get(index(slug, groupSlug)) ~
        put(upsert(slug, groupSlug))
      } ~
      path(Segment) { memberSlugSegment =>
        val memberSlug = Slug.parse(memberSlugSegment)
        delete(destroy(slug, groupSlug, memberSlug))
      }
    }
    // format: ON
}
