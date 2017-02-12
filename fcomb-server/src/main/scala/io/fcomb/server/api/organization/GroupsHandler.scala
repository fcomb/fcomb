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

package io.fcomb.server.api.organization

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.persist.{OrganizationGroupUsersRepo, OrganizationGroupsRepo}
import io.fcomb.rpc.helpers.OrganizationGroupHelpers
import io.fcomb.rpc.OrganizationGroupRequest
import io.fcomb.rpc.ResponseModelWithPk._
import io.fcomb.server.api.organization.group.MembersHandler
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.OrganizationDirectives._
import io.fcomb.server.OrganizationGroupDirectives._
import io.fcomb.server.PaginationDirectives._
import io.fcomb.server.PathMatchers._

object GroupsHandler {
  def index(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      organizationBySlugWithAcl(slug, user.getId(), Role.Admin) { org =>
        extractPagination { pg =>
          import config._
          onSuccess(OrganizationGroupsRepo.paginate(org.getId(), pg)) { p =>
            completePagination(OrganizationGroupsRepo.label, p)
          }
        }
      }
    }

  def create(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      organizationBySlugWithAcl(slug, user.getId(), Role.Admin) { org =>
        entity(as[OrganizationGroupRequest]) { req =>
          import config._
          onSuccess(OrganizationGroupsRepo.create(org.getId(), req)) {
            case Validated.Valid(group) =>
              completeCreated(OrganizationGroupHelpers.response(group))
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  def show(slug: Slug, group: Slug) =
    authenticateUser { user =>
      groupBySlugWithAcl(slug, group, user.getId()) { group =>
        val res = OrganizationGroupHelpers.response(group)
        completeWithEtag(StatusCodes.OK, res)
      }
    }

  def update(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      groupBySlugWithAcl(slug, group, user.getId()) { group =>
        entity(as[OrganizationGroupRequest]) { req =>
          import config._
          onSuccess(OrganizationGroupsRepo.update(group.getId(), user.getId(), req)) {
            case Validated.Valid(updated) =>
              val res = OrganizationGroupHelpers.response(updated)
              complete((StatusCodes.Accepted, res))
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  def destroy(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      groupBySlugWithAcl(slug, group, user.getId()) { group =>
        import config._
        completeAsAccepted(OrganizationGroupsRepo.destroy(group.getId(), user.getId()))
      }
    }

  def suggestions(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      parameter('q) { q =>
        groupBySlugWithAcl(slug, group, user.getId()) { group =>
          import config._
          onSuccess(OrganizationGroupUsersRepo.findSuggestionsUsers(group.getId(), q))(
            completeData)
        }
      }
    }

  def routes(slug: Slug)(implicit config: ApiHandlerConfig): Route =
    // format: OFF
    pathPrefix("groups") {
      pathEnd {
        get(index(slug)) ~
        post(create(slug))
      } ~
      pathPrefix(SlugPath) { group =>
        pathEnd {
          get(show(slug, group)) ~
          put(update(slug, group)) ~
          delete(destroy(slug, group))
        } ~
        path("suggestions" / "members")(get(suggestions(slug, group))) ~
        MembersHandler.routes(slug, group)
      }
    }
    // format: ON
}
