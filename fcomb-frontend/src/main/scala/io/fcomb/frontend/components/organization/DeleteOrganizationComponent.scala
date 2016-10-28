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

package io.fcomb.frontend.components.organization

import cats.data.Xor
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.Rpc
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object DeleteOrganizationComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], orgName: String)
  final case class State(isOpen: Boolean, isValid: Boolean, isDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    def delete(e: ReactTouchEventH): Callback =
      $.state.flatMap { state =>
        if (state.isDisabled) Callback.empty
        else {
          for {
            _     <- $.setState(state.copy(isDisabled = true))
            props <- $.props
            _ <- Callback.future(Rpc.deleteOrganization(props.orgName).map {
              case Xor.Right(_) => props.ctl.set(DashboardRoute.Root)
              case Xor.Left(e)  =>
                // TODO
                updateDisabled(false)
            })
          } yield ()
        }
      }

    def updateDisabled(isDisabled: Boolean): Callback =
      $.modState(_.copy(isDisabled = isDisabled))

    def openDialog(e: ReactTouchEventH): Callback =
      $.modState(_.copy(isOpen = true))

    def closeDialog(e: ReactTouchEventH): Callback =
      $.modState(_.copy(isOpen = false))

    def onRequestClose(buttonClicked: Boolean): Callback =
      $.modState(_.copy(isOpen = false))

    def validateName(e: ReactEventI): Callback = {
      val value = e.target.value.trim()
      $.props.flatMap { props =>
        $.modState(_.copy(isValid = props.orgName == value))
      }
    }

    def render(props: Props, state: State) = {
      val actions = js.Array(
        MuiFlatButton(key = "cancel",
                      label = "Cancel",
                      primary = true,
                      onTouchTap = closeDialog _)(),
        MuiFlatButton(key = "delete",
                      label = "Delete",
                      secondary = true,
                      disabled = !state.isValid,
                      onTouchTap = delete _)()
      )
      <.div(<.h2("Delete repository"),
            MuiDialog(
              title = "Are you sure you want to delete this?",
              actions = actions,
              open = state.isOpen,
              modal = false,
              onRequestClose = onRequestClose _
            )(
              <.div(<.p("Enter this organization’s name to confirm"),
                    MuiTextField(
                      floatingLabelText = "Organization name",
                      onChange = validateName _
                    )())
            ),
            MuiRaisedButton(`type` = "submit",
                            secondary = true,
                            label = "Delete",
                            disabled = state.isDisabled,
                            onTouchTap = openDialog _)())
    }
  }

  private val component = ReactComponentB[Props]("DeleteOrganization")
    .initialState(State(false, false, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], orgName: String) =
    component.apply(Props(ctl, orgName))
}
