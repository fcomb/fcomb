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

package io.fcomb.server

import akka.http.scaladsl.model.headers.OAuth2BearerToken
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.fcomb.models.User
import io.fcomb.services.user.SessionsService

object AuthenticationDirectives {
  def tryAuthenticateUser()(implicit config: ApiHandlerConfig): Directive1[Option[User]] =
    extractCredentials.flatMap {
      case Some(OAuth2BearerToken(token)) => tryFindByToken(token)
      case _ =>
        parameter('token.?).flatMap {
          case Some(token) => tryFindByToken(token)
          case _           => provide(None)
        }
    }

  def authenticateUser()(implicit config: ApiHandlerConfig): Directive1[User] =
    extractCredentials.flatMap {
      case Some(OAuth2BearerToken(token)) => findByToken(token)
      case _ =>
        parameter('token.?).flatMap {
          case Some(token) => findByToken(token)
          case _           => reject(AuthorizationFailedRejection)
        }
    }

  def authorizeAdminUser()(implicit config: ApiHandlerConfig): Directive1[User] =
    authenticateUser.flatMap { user =>
      if (user.isAdmin) provide(user)
      else complete(HttpResponse(StatusCodes.Forbidden))
    }

  private def tryFindByToken(token: String)(
      implicit config: ApiHandlerConfig): Directive1[Option[User]] = {
    import config._
    onSuccess(SessionsService.find(token)).flatMap(provide)
  }

  private def findByToken(token: String)(implicit config: ApiHandlerConfig): Directive1[User] =
    tryFindByToken(token).flatMap {
      case Some(user) => provide(user)
      case None       => reject(AuthorizationFailedRejection)
    }
}
