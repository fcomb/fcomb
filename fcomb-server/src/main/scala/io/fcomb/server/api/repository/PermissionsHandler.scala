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

package io.fcomb.server.api.repository

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import cats.data.Validated
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution.ImageKey
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.rpc.acl.PermissionUserCreateRequest
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.PaginationDirectives._

object PermissionsHandler {
  val servicePath = "permissions"

  def index(key: ImageKey) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Manage) { image =>
          extractPagination { pg =>
            onSuccess(PermissionsRepo.findByImageIdWithPagination(image, pg)) { p =>
              completePagination(PermissionsRepo.label, p)
            }
          }
        }
      }
    }
  }

  def upsert(key: ImageKey) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Manage) { image =>
          entity(as[PermissionUserCreateRequest]) { req =>
            onSuccess(PermissionsRepo.upsertByImage(image, req)) {
              case Validated.Valid(p)   => complete((StatusCodes.Accepted, p))
              case Validated.Invalid(e) => ??? // TODO
            }
          }
        }
      }
    }
  }

  def routes(key: ImageKey): Route = {
    // format: OFF
    path(servicePath) {
      get(index(key)) ~
      put(upsert(key))
    }
    // format: ON
  }
}
