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

import org.scalajs.dom.window
import scala.util.Try

object AuthService {
  def setToken(token: String): Unit = {
    window.localStorage.setItem(sessionKey, token)
  }

  def getToken(): Option[String] = {
    Try(window.localStorage.getItem(sessionKey)).toOption
  }

  def removeToken(): Unit = {
    window.localStorage.removeItem(sessionKey)
  }

  private val sessionKey = "sessionToken"
}
