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

package io.fcomb.frontend.services

import cats.data.Xor
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.dispatcher.actions.Authenticated
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.models.errors.Error
import org.scalajs.dom.window
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

object AuthService {
  def authentication(email: String, password: String)(
      implicit ec: ExecutionContext): Future[Xor[Seq[Error], Unit]] =
    Rpc.signIn(email.trim(), password.trim()).map {
      case Xor.Right(session) => Xor.Right(AppCircuit.dispatch(Authenticated(session.token)))
      case res @ Xor.Left(e)  => res
    }

  def setToken(token: String): Unit =
    window.localStorage.setItem(sessionKey, token)

  def getToken(): Option[String] =
    Try(window.localStorage.getItem(sessionKey)) match {
      case Success(s) if s != null && s.nonEmpty => Some(s)
      case _                                     => None
    }

  def removeToken(): Unit =
    window.localStorage.removeItem(sessionKey)

  private val sessionKey = "sessionToken"
}
