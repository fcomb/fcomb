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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.fcomb.json.models.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution.ImageKey
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.PaginationDirectives._

// TODO
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.circe.java8.time._

object PermissionsHandler {
  val servicePath = "permissions"

  def index(key: ImageKey) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Manage) { image =>
          extractPagination { pg =>
            onSuccess(PermissionsRepo.findByImageIdWithPagination(image.getId, pg)) { p =>
              completePagination(PermissionsRepo.label, p)
            }
          }
        }
      }
    }
  }

  def routes(key: ImageKey): Route = {
    // format: OFF
    path(servicePath) {
      get(index(key))
    }
    // format: ON
  }
}
