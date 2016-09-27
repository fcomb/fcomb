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

package io.fcomb.frontend.components.repository

import cats.data.Xor
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Resource, Rpc, RpcMethod}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object DeleteRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], repositoryName: String)
  final case class State(isOpen: Boolean, isDisabled: Boolean)

  class Backend($ : BackendScope[Props, State]) {
    def delete(e: ReactTouchEventH): Callback =
      (for {
        props <- $.props
        state <- $.state
      } yield (props, state)).flatMap {
        case (props, state) if !state.isDisabled =>
          $.setState(state.copy(isDisabled = true)) >>
            Callback.future {
              Rpc
                .call[Unit](RpcMethod.DELETE, Resource.repository(props.repositoryName))
                .map {
                  case Xor.Right(_) => props.ctl.set(DashboardRoute.Root)
                  case Xor.Left(e)  =>
                    // TODO
                    updateDisabled(false)
                }
                .recover {
                  case _ => updateDisabled(false)
                }
            }
      }

    def openDialog(e: ReactTouchEventH): Callback =
      $.modState(_.copy(isOpen = true))

    def closeDialog(e: ReactTouchEventH): Callback =
      $.modState(_.copy(isOpen = false))

    def onRequestClose(buttonClicked: Boolean): Callback =
      $.modState(_.copy(isOpen = false))

    def updateDisabled(isDisabled: Boolean): Callback =
      $.modState(_.copy(isDisabled = isDisabled))

    lazy val actions = js.Array(
      MuiFlatButton(key = "cancel",
                    label = "Cancel",
                    primary = true,
                    onTouchTap = closeDialog _)(),
      MuiFlatButton(key = "delete", label = "Delete", primary = true, onTouchTap = delete _)()
    )

    def render(props: Props, state: State) =
      <.div(<.h2("Delete repository"),
            MuiDialog(
              title = "Are you sure you want to delete this?",
              actions = actions,
              open = state.isOpen,
              modal = false,
              onRequestClose = onRequestClose _
            )(),
            MuiRaisedButton(`type` = "submit",
                            secondary = true,
                            label = "Delete",
                            disabled = state.isDisabled,
                            onTouchTap = openDialog _)())
  }

  private val component = ReactComponentB[Props]("DeleteRepository")
    .initialState(State(false, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String) =
    component.apply(Props(ctl, repositoryName))
}
