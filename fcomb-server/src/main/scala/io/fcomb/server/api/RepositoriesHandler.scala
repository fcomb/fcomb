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
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution.{ImageKey, ImageVisibilityKind}
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.rpc.docker.distribution.ImageUpdateRequest
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.api.repository._

object RepositoriesHandler {
  val servicePath = "repositories"

  def show(key: ImageKey) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Read) { image =>
          val res = ImageHelpers.responseFrom(image)
          complete((StatusCodes.OK, res))
        }
      }
    }
  }

  def update(key: ImageKey) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Manage) { image =>
          entity(as[ImageUpdateRequest]) { req =>
            onSuccess(ImagesRepo.update(image.getId(), req)) {
              case Validated.Valid(image) =>
                val res = ImageHelpers.responseFrom(image)
                complete((StatusCodes.Accepted, res))
              case Validated.Invalid(e) =>
                ??? // TODO
            }
          }
        }
      }
    }
  }

  def updateVisibility(key: ImageKey, visibilityKind: ImageVisibilityKind) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Manage) { image =>
          onSuccess(ImagesRepo.updateVisibility(image.getId(), visibilityKind)) { _ =>
            completeAccepted()
          }
        }
      }
    }
  }

  def destroy(key: ImageKey) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Manage) { image =>
          ???
        }
      }
    }
  }

  def routes(key: ImageKey): Route = {
    // format: OFF
    pathEnd {
      get(show(key)) ~
      put(update(key)) ~
      delete(destroy(key))
    } ~
    pathPrefix("visibility") {
      path("public")(post(updateVisibility(key, ImageVisibilityKind.Public))) ~
      path("private")(post(updateVisibility(key, ImageVisibilityKind.Private)))
    } ~
    TagsHandler.routes(key) ~
    PermissionsHandler.routes(key)
    // format: ON
  }
}
