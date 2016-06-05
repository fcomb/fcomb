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
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import io.fcomb.persist.UsersRepo
import io.fcomb.models.User

trait AuthenticationDirectives {
  def authenticateUserBasic: Directive1[User] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extractCredentials.flatMap {
        case Some(BasicHttpCredentials(username, password)) ⇒
          onSuccess(UsersRepo.matchByUsernameAndPassword(username, password)).flatMap {
            case Some(user) ⇒ provide(user)
            case None       ⇒ reject(AuthorizationFailedRejection)
          }
        case _ ⇒ reject(AuthorizationFailedRejection)
      }
    }
}

object AuthenticationDirectives extends AuthenticationDirectives
