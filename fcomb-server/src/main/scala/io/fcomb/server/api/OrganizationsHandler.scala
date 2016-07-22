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
import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.util.FastFuture, FastFuture._
import cats.data.Validated
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.persist.OrganizationsRepo
import io.fcomb.rpc.{OrganizationCreateRequest, OrganizationUpdateRequest}
import io.fcomb.rpc.helpers.OrganizationHelpers
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CirceSupport._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.OrganizationDirectives._
import io.fcomb.server.SlugPath
import scala.collection.immutable

object OrganizationsHandler {
  val servicePath = "organizations"

  lazy val resourcePrefix = s"/$apiVersion/$servicePath/"

  def create = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        entity(as[OrganizationCreateRequest]) { req =>
          onSuccess(OrganizationsRepo.create(req, user.getId())) {
            case Validated.Valid(org) =>
              val uri     = resourcePrefix + org.getId().toString
              val headers = immutable.Seq(Location(uri))
              val res     = OrganizationHelpers.responseFrom(org, Role.Admin)
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

  def show(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      tryAuthenticateUser { userOpt =>
        val futRes = userOpt match {
          case Some(user) => OrganizationsRepo.findWithRoleBySlug(slug, user.getId())
          case _          => OrganizationsRepo.findBySlug(slug).fast.map(_.map(org => (org, None)))
        }
        onSuccess(futRes) {
          case Some((org, role)) =>
            val res = OrganizationHelpers.responseFrom(org, role)
            complete((StatusCodes.OK, res))
          case _ => completeNotFound()
        }
      }
    }
  }

  def update(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        organizationBySlugWithAcl(slug, user.getId(), Role.Admin) { org =>
          entity(as[OrganizationUpdateRequest]) { req =>
            onSuccess(OrganizationsRepo.update(org.getId(), req)) {
              case Validated.Valid(updated) =>
                val res = OrganizationHelpers.responseFrom(updated, Role.Admin)
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
        organizationBySlugWithAcl(slug, user.getId(), Role.Admin) { org =>
          onSuccess(OrganizationsRepo.destroy(org.getId())) { _ =>
            completeAccepted()
          }
        }
      }
    }
  }

  val routes: Route = {
    // format: OFF
    pathPrefix(servicePath) {
      pathEnd {
        post(create)
      } ~
      pathPrefix(SlugPath) { slug =>
        pathEnd {
          get(show(slug)) ~
          put(update(slug)) ~
          delete(destroy(slug))
        } ~
        organization.RepositoriesHandler.routes(slug)
      }
    }
    // format: ON
  }
}
