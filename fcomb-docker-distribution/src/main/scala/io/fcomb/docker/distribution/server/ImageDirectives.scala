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

package io.fcomb.docker.distribution.server

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._
import io.fcomb.models.User
import io.fcomb.models.docker.distribution.Image
import io.fcomb.models.errors.docker.distribution._
import io.fcomb.persist.docker.distribution.ImagesRepo
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._

trait ImageDirectives {
  def imageByNameWithAcl(imageName: String, user: User): Directive1[Image] = {
    extractExecutionContext.flatMap { implicit ec =>
      onSuccess(ImagesRepo.findByImageAndUserId(imageName, user.getId)).flatMap {
        case Some(user) => provide(user)
        case None =>
          complete(
            StatusCodes.NotFound,
            DistributionErrorResponse.from(DistributionError.NameUnknown())
          )
      }
    }
  }
}

object ImageDirectives extends ImageDirectives
