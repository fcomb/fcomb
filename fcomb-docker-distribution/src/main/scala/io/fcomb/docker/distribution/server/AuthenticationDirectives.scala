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

import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.fcomb.json.models.errors.docker.distribution.Formats._
import io.fcomb.models.errors.docker.distribution.{DistributionErrorResponse, DistributionError}
import io.fcomb.models.User
import io.fcomb.persist.UsersRepo
import io.fcomb.server.CirceSupport._

trait AuthenticationDirectives {
  def authenticateUserBasic: Directive1[User] =
    extractExecutionContext.flatMap { implicit ec =>
      extractCredentials.flatMap {
        case Some(BasicHttpCredentials(username, password)) =>
          onSuccess(UsersRepo.matchByUsernameAndPassword(username, password)).flatMap {
            case Some(user) => provide(user)
            case None       => complete(unauthorizedError)
          }
        case _ => complete(unauthorizedError)
      }
    }

  private val unauthorizedError = (
    StatusCodes.Unauthorized,
    DistributionErrorResponse.from(DistributionError.Unauthorized())
  )
}

object AuthenticationDirectives extends AuthenticationDirectives
