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
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.persist.OrganizationsRepo
import io.fcomb.rpc.helpers.OrganizationHelpers
import io.fcomb.rpc.OrganizationCreateRequest
import io.fcomb.rpc.ResponseModelWithPk._
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.OrganizationDirectives._
import io.fcomb.server.PathMatchers._

object OrganizationsHandler {
  def create()(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      entity(as[OrganizationCreateRequest]) { req =>
        import config._
        onSuccess(OrganizationsRepo.create(req, user.getId())) {
          case Validated.Valid(org) =>
            completeCreated(OrganizationHelpers.response(org, Role.Admin))
          case Validated.Invalid(errs) => completeErrors(errs)
        }
      }
    }

  def show(slug: Slug)(implicit config: ApiHandlerConfig) =
    tryAuthenticateUser { userOpt =>
      import config._
      val futRes = userOpt match {
        case Some(user) => OrganizationsRepo.findWithRoleBySlug(slug, user.getId())
        case _          => OrganizationsRepo.findBySlug(slug).map(_.map(org => (org, None)))
      }
      onSuccess(futRes) {
        case Some((org, role)) =>
          completeWithEtag(StatusCodes.OK, OrganizationHelpers.response(org, role))
        case _ => completeNotFound()
      }
    }

  // def update(slug: Slug) = {
  //     authenticateUser { user =>
  //       organizationBySlugWithAcl(slug, user.getId(), Role.Admin) { org =>
  //         entity(as[OrganizationUpdateRequest]) { req =>
  //           onSuccess(OrganizationsRepo.update(org.getId(), req)) {
  //             case Validated.Valid(updated) =>
  //               val res = OrganizationHelpers.response(updated, Role.Admin)
  //               complete((StatusCodes.Accepted, res))
  //             case Validated.Invalid(errs) => completeErrors(errs)
  //           }
  //         }
  //       }
  //     }
  //   }

  def destroy(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser { user =>
      organizationBySlugWithAcl(slug, user.getId(), Role.Admin) { org =>
        import config._
        onSuccess(OrganizationsRepo.destroy(org.getId())) { _ =>
          completeAccepted()
        }
      }
    }

  def routes()(implicit config: ApiHandlerConfig): Route =
    // format: OFF
    pathPrefix("organizations") {
      pathEnd(post(create())) ~
      pathPrefix(SlugPath) { slug =>
        pathEnd {
          get(show(slug)) ~
          // put(update(slug)) ~
          delete(destroy(slug))
        } ~
        organization.GroupsHandler.routes(slug) ~
        organization.RepositoriesHandler.routes(slug)
      }
    }
    // format: ON
}
