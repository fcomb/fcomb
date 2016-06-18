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

package io.fcomb.frontend.dispatcher

import diode.Circuit
import diode.react.ReactConnector
import io.fcomb.frontend.services.AuthService

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  import diode._
  val treeHandler = new ActionHandler(zoomRW(_.session)((m, v) => m.copy(session = v))) {
    override def handle = {
      case actions.Initialize =>
        AuthService.getToken() match {
          case Some(token) => updated(Some(token))
          case None        => noChange
        }
      case actions.Authenticated(token) =>
        AuthService.setToken(token)
        updated(Some(token))
    }
  }

  protected def actionHandler = composeHandlers(treeHandler)

  protected def initialModel = RootModel(session = None)

  def currentState = zoom(identity).value

  def session = currentState.session
}
