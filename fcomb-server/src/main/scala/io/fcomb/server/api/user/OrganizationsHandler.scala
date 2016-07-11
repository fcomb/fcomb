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

package io.fcomb.server.api.user

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.PaginationDirectives._

object OrganizationsHandler {
  val servicePath = "organizations"

  def index = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        extractPagination { pg =>
          // onSuccess(???) { p =>
          //   completePagination(???, p)
          // }
          ???
        }
      }
    }
  }

  val routes: Route = {
    // format: OFF
    path(servicePath) {
      get(index)
    }
    // format: ON
  }
}
