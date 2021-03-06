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

package io.fcomb.server.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.common.Slug
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.rpc.docker.distribution.ImageUpdateRequest
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.server.api.repository._
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.PersistDirectives._

object RepositoriesHandler {
  def show(slug: Slug)(implicit config: ApiHandlerConfig) =
    tryAuthenticateUser.apply { userOpt =>
      imageAndActionRead(slug, userOpt).apply {
        case (image, action) =>
          completeWithEtag(StatusCodes.OK, ImageHelpers.response(image, action))
      }
    }

  def update(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      imageAndAction(slug, user.getId(), Action.Manage).apply {
        case (image, action) =>
          entity(as[ImageUpdateRequest]) { req =>
            import config.ec
            transact(ImagesRepo.update(image.getId(), req)).apply {
              case Validated.Valid(updated) =>
                complete((StatusCodes.Accepted, ImageHelpers.response(updated, action)))
              case Validated.Invalid(errs) => completeErrors(errs)
            }
          }
      }
    }

  def updateVisibility(slug: Slug, visibilityKind: ImageVisibilityKind)(
      implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      image(slug, user.getId(), Action.Manage).apply { image =>
        transact(ImagesRepo.updateVisibility(image.getId(), visibilityKind)).apply(_ =>
          completeAccepted())
      }
    }

  def destroy(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      image(slug, user.getId(), Action.Manage).apply { image =>
        import config.ec
        transact(ImagesRepo.destroy(image.getId())).apply(completeAsAccepted(_))
      }
    }

  def routes()(implicit config: ApiHandlerConfig): Route =
    // format: off
    pathPrefix("repositories") {
      pathPrefix(IntNumber) { id =>
        nestedRoutes(Slug.Id(id))
      } ~
      pathPrefix(Segments(2)) { xs =>
        nestedRoutes(Slug.Name(xs.mkString("/")))
      }
    }
    // format: on

  private def nestedRoutes(slug: Slug)(implicit config: ApiHandlerConfig): Route =
    // format: off
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
    // format: on
}
