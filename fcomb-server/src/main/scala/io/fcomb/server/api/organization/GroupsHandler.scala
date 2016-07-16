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
import io.fcomb.rpc.helpers.OrganizationGroupHelpers
import io.fcomb.rpc.OrganizationGroupCreateRequest
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CirceSupport._
import io.fcomb.server.OrganizationDirectives._
import io.fcomb.server.api.{OrganizationGroupsHandler, apiVersion}
import scala.collection.immutable

object GroupsHandler {
  val servicePath = "groups"

  lazy val resourcePrefix = s"/$apiVersion/${OrganizationGroupsHandler.servicePath}/"

  def create(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        organizationBySlugWithAcl(slug, user.getId(), Role.Admin) { org =>
          entity(as[OrganizationGroupCreateRequest]) { req =>
            onSuccess(OrganizationGroupsRepo.create(org.getId(), req)) {
              case Validated.Valid(group) =>
                val uri     = resourcePrefix + org.getId().toString
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

  def routes(slug: Slug): Route = {
    // format: OFF
    path(servicePath) {
      post(create(slug))
    }
    // format: ON
  }
}
