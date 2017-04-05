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
import io.fcomb.server.PersistDirectives._

object GroupsHandler {
  def index(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      organizationBySlugWithAcl(slug, user.getId(), Role.Admin).apply { org =>
        extractPagination { pg =>
          import config.ec
          transact(OrganizationGroupsRepo.paginate(org.getId(), pg))
            .apply(completePagination(OrganizationGroupsRepo.label, _))
        }
      }
    }

  def create(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      organizationBySlugWithAcl(slug, user.getId(), Role.Admin).apply { org =>
        entity(as[OrganizationGroupRequest]) { req =>
          import config.ec
          transact(OrganizationGroupsRepo.create(org.getId(), req)).apply {
            case Validated.Valid(group) =>
              completeCreated(OrganizationGroupHelpers.response(group))
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  def show(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      groupBySlugWithAcl(slug, group, user.getId()).apply { group =>
        completeWithEtag(StatusCodes.OK, OrganizationGroupHelpers.response(group))
      }
    }

  def update(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      groupBySlugWithAcl(slug, group, user.getId()).apply { group =>
        entity(as[OrganizationGroupRequest]) { req =>
          import config.ec
          transact(OrganizationGroupsRepo.update(group.getId(), user.getId(), req)).apply {
            case Validated.Valid(updated) =>
              complete((StatusCodes.Accepted, OrganizationGroupHelpers.response(updated)))
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  def destroy(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      groupBySlugWithAcl(slug, group, user.getId()).apply { group =>
        import config.ec
        transact(OrganizationGroupsRepo.destroy(group.getId(), user.getId()))
          .apply(completeAsAccepted(_))
      }
    }

  def suggestions(slug: Slug, group: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      parameter('q) { q =>
        groupBySlugWithAcl(slug, group, user.getId()).apply { group =>
          import config.ec
          transact(OrganizationGroupUsersRepo.findSuggestionsUsers(group.getId(), q))
            .apply(completeData)
        }
      }
    }

  def routes(slug: Slug)(implicit config: ApiHandlerConfig): Route =
    // format: off
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
    // format: on
}
