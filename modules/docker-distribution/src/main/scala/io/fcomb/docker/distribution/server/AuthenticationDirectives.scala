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

package io.fcomb.docker.distribution.server

import akka.http.scaladsl.model.headers.{`WWW-Authenticate`, BasicHttpCredentials, HttpChallenges}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.fcomb.docker.distribution.server.Api.defaultHeaders
import io.fcomb.docker.distribution.server.CommonDirectives._
import io.fcomb.models.errors.docker.distribution.DistributionError
import io.fcomb.models.User
import io.fcomb.persist.UsersRepo
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.PersistDirectives._

object AuthenticationDirectives {
  def tryAuthenticateUserBasic()(implicit config: ApiHandlerConfig): Directive1[Option[User]] =
    extractCredentials.flatMap {
      case Some(BasicHttpCredentials(username, password)) =>
        import config.ec
        transact(UsersRepo.matchByUsernameAndPassword(username, password)).flatMap(provide)
      case _ => provide(None)
    }

  def authenticateUserBasic()(implicit config: ApiHandlerConfig): Directive1[User] =
    tryAuthenticateUserBasic.flatMap {
      case Some(user) => provide(user)
      case None       => unauthorizedError()
    }

  private def unauthorizedError[T]()(implicit config: ApiHandlerConfig): Directive1[T] =
    Directive { _ =>
      val authenticateHeader =
        `WWW-Authenticate`(HttpChallenges.basic(config.settings.security.realm))
      respondWithHeaders(authenticateHeader :: defaultHeaders) {
        completeError(DistributionError.unauthorized)
      }
    }
}
