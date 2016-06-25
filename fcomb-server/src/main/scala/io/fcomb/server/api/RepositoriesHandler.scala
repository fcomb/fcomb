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
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution.Image
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.ImageDirectives._

object RepositoriesHandler {
  val servicePath = "repositories"

  def show(name: String) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByNameWithAcl(name, user, Action.Read)(completeImage)
      }
    }

  def show(id: Long) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByIdWithAcl(id, user, Action.Read)(completeImage)
      }
    }

  private def completeImage(image: Image) = {
    val res = ImageHelpers.responseFrom(image)
    complete((StatusCodes.OK, res))
  }
}
