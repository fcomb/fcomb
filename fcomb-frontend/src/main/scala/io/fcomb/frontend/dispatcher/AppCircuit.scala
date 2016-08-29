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
import io.circe.parser.decode
import io.fcomb.frontend.dispatcher.handlers._
import io.fcomb.json.models.Formats.decodeSessionPayloadUser
import io.fcomb.models.SessionPayload
import org.scalajs.dom.window

object AppCircuit extends Circuit[RootModel] with ReactConnector[RootModel] {
  protected def actionHandler = composeHandlers(
    new AuthenticationHandler(zoomRW(_.session)((m, v) => m.copy(session = v)))
  )

  protected def initialModel = RootModel(session = None)

  def currentState = zoom(identity).value

  def session = currentState.session

  def currentUser =
    session
      .flatMap(_.split('.').lift(1))
      .flatMap(s => decode[SessionPayload.User](window.atob(s)).toOption)
}
