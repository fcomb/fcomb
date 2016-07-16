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
import cats.data.Validated
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.common.Slug
import io.fcomb.persist.OrganizationGroupsRepo
import io.fcomb.rpc.OrganizationGroupUpdateRequest
import io.fcomb.rpc.helpers.OrganizationGroupHelpers
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CirceSupport._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.OrganizationGroupDirectives._
import io.fcomb.server.SlugPath

object OrganizationGroupsHandler {
  val servicePath = "groups"

  def show(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        groupBySlugWithAcl(slug, user.getId()) { group =>
          val res = OrganizationGroupHelpers.responseFrom(group)
          complete((StatusCodes.OK, res))
        }
      }
    }
  }

  def update(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        groupBySlugWithAcl(slug, user.getId()) { group =>
          entity(as[OrganizationGroupUpdateRequest]) { req =>
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

  def destroy(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        groupBySlugWithAcl(slug, user.getId()) { group =>
          onSuccess(OrganizationGroupsRepo.destroy(group.getId())) { _ =>
            completeAccepted()
          }
        }
      }
    }
  }

  val routes: Route = {
    // format: OFF
    path(servicePath / SlugPath) { slug =>
      get(show(slug)) ~
      put(update(slug)) ~
      delete(destroy(slug))
    }
    // format: ON
  }
}
