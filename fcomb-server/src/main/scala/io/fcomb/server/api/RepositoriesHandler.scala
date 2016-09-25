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
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.common.Slug
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.rpc.docker.distribution.ImageUpdateRequest
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.server.api.repository._
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CirceSupport._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.ImageDirectives._

object RepositoriesHandler {
  val servicePath = "repositories"

  def show(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      tryAuthenticateUser { userOpt =>
        imageBySlugRead(slug, userOpt) { image =>
          val res = ImageHelpers.responseFrom(image, Action.Read)
          completeWithEtag(StatusCodes.OK, res)
        }
      }
    }
  }

  def update(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageWithActionBySlugWithAcl(slug, user.getId(), Action.Manage) {
          case (image, action) =>
            entity(as[ImageUpdateRequest]) { req =>
              onSuccess(ImagesRepo.update(image.getId(), req)) {
                case Validated.Valid(updated) =>
                  val res = ImageHelpers.responseFrom(updated, action)
                  complete((StatusCodes.Accepted, res))
                case Validated.Invalid(errs) => completeErrors(errs)
              }
            }
        }
      }
    }
  }

  def updateVisibility(slug: Slug, visibilityKind: ImageVisibilityKind) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageBySlugWithAcl(slug, user.getId(), Action.Manage) { image =>
          onSuccess(ImagesRepo.updateVisibility(image.getId(), visibilityKind)) { _ =>
            completeAccepted()
          }
        }
      }
    }
  }

  def destroy(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageBySlugWithAcl(slug, user.getId(), Action.Manage) { image =>
          onSuccess(ImagesRepo.destroy(image.getId())) { _ =>
            completeAccepted()
          }
        }
      }
    }
  }

  val routes: Route = {
    // format: OFF
    pathPrefix(servicePath) {
      pathPrefix(IntNumber) { id =>
        nestedRoutes(Slug.Id(id))
      } ~
      pathPrefix(Segments(2)) { xs =>
        val name = xs.mkString("/")
        nestedRoutes(Slug.Name(name))
      }
    }
    // format: ON
  }

  private def nestedRoutes(slug: Slug): Route = {
    // format: OFF
    pathEnd {
      get(show(slug)) ~
      put(update(slug)) ~
      delete(destroy(slug))
    } ~
    pathPrefix("visibility") {
      path("public")(post(updateVisibility(slug, ImageVisibilityKind.Public))) ~
      path("private")(post(updateVisibility(slug, ImageVisibilityKind.Private)))
    } ~
    TagsHandler.routes(slug) ~
    PermissionsHandler.routes(slug) ~
    WebhooksHandler.routes(slug)
    // format: ON
  }
}
