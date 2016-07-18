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

package io.fcomb.server.api.users

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.common.Slug
import io.fcomb.persist.UsersRepo
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.PaginationDirectives._

object RepositoriesHandler {
  val servicePath = "repositories"

  def index(slug: Slug) = {
    extractExecutionContext { implicit ec =>
      tryAuthenticateUser { currentUserOpt =>
        onSuccess(UsersRepo.findBySlug(slug)) {
          case Some(user) =>
            extractPagination { pg =>
              val res = ImagesRepo.findByUserOwnerWithPagination(user.getId(),
                                                                 currentUserOpt.flatMap(_.id),
                                                                 pg)
              onSuccess(res) { p =>
                completePagination(ImagesRepo.label, p)
              }
            }
          case None => completeNotFound()
        }
      }
    }
  }

  def routes(slug: Slug): Route = {
    // format: OFF
    path(servicePath) {
      get(index(slug))
    }
    // format: ON
  }
}
