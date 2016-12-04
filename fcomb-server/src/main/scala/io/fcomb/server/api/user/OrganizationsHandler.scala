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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.fcomb.json.rpc.Formats._
import io.fcomb.persist.OrganizationsRepo
import io.fcomb.server.api.{ApiHandler, ApiHandlerConfig}
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.PaginationDirectives._

final class OrganizationsHandler(implicit val config: ApiHandlerConfig) extends ApiHandler {
  final def index =
    authenticateUser { user =>
      extractPagination { pg =>
        onSuccess(OrganizationsRepo.paginateAvailableByUserId(user.getId(), pg)) { p =>
          completePagination(OrganizationsRepo.label, p)
        }
      }
    }

  final val routes: Route =
    // format: OFF
    path("organizations") {
      get(index)
    }
    // format: ON
}
