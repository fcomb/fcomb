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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.{Action, Role}
import io.fcomb.models.common.Slug
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.rpc.docker.distribution.ImageCreateRequest
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.rpc.ResponseModelWithPk._
import io.fcomb.server.api.apiVersion
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CirceSupport._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.OrganizationDirectives._
import io.fcomb.server.PaginationDirectives._

object RepositoriesHandler {
  val handlerPath = "repositories"

  lazy val resourcePrefix = s"/$apiVersion/${RepositoriesHandler.handlerPath}/"

  def index(slug: Slug) =
    extractExecutionContext { implicit ec =>
      organizationBySlug(slug) { org =>
        tryAuthenticateUser { userOpt =>
          extractPagination { pg =>
            val res = ImagesRepo.paginateAvailableByOrganizationOwner(org.getId(),
                                                                      userOpt.flatMap(_.id),
                                                                      pg)
            onSuccess(res) { p =>
              completePagination(ImagesRepo.label, p)
            }
          }
        }
      }
    }

  def create(slug: Slug) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        val userId = user.getId()
        organizationBySlugWithAcl(slug, userId, Role.Admin) { org =>
          entity(as[ImageCreateRequest]) { req =>
            onSuccess(ImagesRepo.create(req, org, userId)) {
              case Validated.Valid(image) =>
                val res = ImageHelpers.responseFrom(image, Action.Manage)
                completeCreated(res, resourcePrefix)
              case Validated.Invalid(errs) => completeErrors(errs)
            }
          }
        }
      }
    }

  def routes(slug: Slug): Route =
    // format: OFF
    path(handlerPath) {
      get(index(slug)) ~
      post(create(slug))
    }
    // format: ON
}
