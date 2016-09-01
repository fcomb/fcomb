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

package io.fcomb.server.api.organization

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.persist.OrganizationGroupsRepo
import io.fcomb.rpc.OrganizationGroupRequest
import io.fcomb.rpc.helpers.OrganizationGroupHelpers
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.CirceSupport._
import io.fcomb.server.OrganizationDirectives._
import io.fcomb.server.OrganizationGroupDirectives._
import io.fcomb.server.PaginationDirectives._
import io.fcomb.server.SlugPath
import io.fcomb.server.api.OrganizationsHandler
import scala.collection.immutable

object GroupsHandler {
  val servicePath = "groups"

  lazy val resourcePrefix = s"${OrganizationsHandler.resourcePrefix}/"

  def index(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        organizationBySlugWithAcl(slug, user.getId(), Role.Admin) { org =>
          extractPagination { pg =>
            onSuccess(OrganizationGroupsRepo.paginateByOrgId(org.getId(), pg)) { p =>
              completePagination(OrganizationGroupsRepo.label, p)
            }
          }
        }
      }
    }
  }

  def create(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        organizationBySlugWithAcl(slug, user.getId(), Role.Admin) { org =>
          entity(as[OrganizationGroupRequest]) { req =>
            onSuccess(OrganizationGroupsRepo.create(org.getId(), req)) {
              case Validated.Valid(group) =>
                val uri     = s"${resourcePrefix}/$slug/$servicePath/${org.getId()}"
                val headers = immutable.Seq(Location(uri))
                val res     = OrganizationGroupHelpers.responseFrom(group)
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
  }

  def show(slug: Slug, groupSlug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        groupBySlugWithAcl(slug, groupSlug, user.getId()) { group =>
          val res = OrganizationGroupHelpers.responseFrom(group)
          completeWithEtag(StatusCodes.OK, res)
        }
      }
    }
  }

  def update(slug: Slug, groupSlug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        groupBySlugWithAcl(slug, groupSlug, user.getId()) { group =>
          entity(as[OrganizationGroupRequest]) { req =>
            onSuccess(OrganizationGroupsRepo.update(group.getId(), req)) {
              case Validated.Valid(updated) =>
                val res = OrganizationGroupHelpers.responseFrom(updated)
                complete((StatusCodes.Accepted, res))
              case Validated.Invalid(e) =>
                ??? // TODO
            }
          }
        }
      }
    }
  }

  def destroy(slug: Slug, groupSlug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        groupBySlugWithAcl(slug, groupSlug, user.getId()) { group =>
          onSuccess(OrganizationGroupsRepo.destroy(group.getId())) { _ =>
            completeAccepted()
          }
        }
      }
    }
  }

  def routes(slug: Slug): Route = {
    // format: OFF
    pathPrefix(servicePath) {
      pathEnd {
        get(index(slug)) ~
        post(create(slug))
      } ~
      pathPrefix(SlugPath) { groupSlug =>
        pathEnd {
          get(show(slug, groupSlug)) ~
          put(update(slug, groupSlug)) ~
          delete(destroy(slug, groupSlug))
        } ~
        group.MembersHandler.routes(slug, groupSlug)
      }
    }
    // format: ON
  }
}
