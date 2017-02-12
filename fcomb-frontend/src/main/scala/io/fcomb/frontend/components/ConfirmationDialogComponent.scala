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

package io.fcomb.frontend.components

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import japgolly.scalajs.react._
import scala.scalajs.js

object ConfirmationDialogComponent {
  final case class Props(title: String,
                         actions: js.Array[ReactComponentU_],
                         isModal: Boolean,
                         isOpen: Boolean,
                         cb: Boolean => Callback,
                         children: ReactNode*)

  final class Backend($ : BackendScope[Props, Unit]) {
    def updateOpenState(isOpen: Boolean) = $.props.flatMap(_.cb(isOpen))

    def closeDialog(e: ReactTouchEventH): Callback = updateOpenState(false)

    def onRequestClose(buttonClicked: Boolean): Callback = updateOpenState(false)

    lazy val defaultActions = js.Array(
      MuiFlatButton(key = "cancel",
                    label = "Cancel",
                    primary = true,
                    onTouchTap = closeDialog _)())

    def render(props: Props) =
      MuiDialog(
        title = props.title,
        actions = defaultActions.concat(props.actions),
        open = props.isOpen,
        modal = props.isModal,
        onRequestClose = onRequestClose _
      )(props.children)
  }

  private val component = ReactComponentB[Props]("ConfirmationDialog").renderBackend[Backend].build

  def apply(title: String,
            actions: js.Array[ReactComponentU_],
            isModal: Boolean,
            isOpen: Boolean,
            cb: Boolean => Callback,
            children: ReactNode*) =
    component.apply(Props(title, actions, isModal, isOpen, cb, children))
}
