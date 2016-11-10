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

package io.fcomb.frontend.dispatcher.handlers

import cats.syntax.either._
import diode.{ActionHandler, ActionResult, ModelRW}
import io.circe.parser.decode
import io.fcomb.frontend.dispatcher.actions._
import io.fcomb.frontend.services.AuthService
import io.fcomb.json.models.Formats.decodeSessionPayloadUser
import io.fcomb.models.SessionPayload
import org.scalajs.dom.window

final class AuthenticationHandler[M](
    modelRW: ModelRW[M, (Option[String], Option[SessionPayload.User])])
    extends ActionHandler(modelRW) {
  val pf: PartialFunction[AuthenticationAction, ActionResult[M]] = {
    case LoadSession =>
      AuthService.getToken() match {
        case Some(token) => applyToken(token)
        case None        => noChange
      }
    case Authenticated(token) =>
      AuthService.setToken(token)
      applyToken(token)
    case LogOut =>
      AuthService.removeToken()
      updated((None, None))
  }

  private def applyToken(token: String) =
    updated((Some(token), decodeUser(token)))

  private def decodeUser(session: String): Option[SessionPayload.User] =
    session.split('.').lift(1).flatMap { body =>
      decode[SessionPayload.User](window.atob(body)).toOption
    }

  protected def handle = { case action: AuthenticationAction => pf(action) }
}
