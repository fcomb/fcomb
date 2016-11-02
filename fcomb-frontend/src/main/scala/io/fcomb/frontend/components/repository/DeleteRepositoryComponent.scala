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
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.{
  AlertDialogComponent,
  ConfirmationDialogComponent,
  LayoutComponent
}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object DeleteRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String)
  final case class State(isDisabled: Boolean, isConfirmationOpen: Boolean, error: Option[String])

  final class Backend($ : BackendScope[Props, State]) {
    def delete(e: ReactTouchEventH): Callback =
      (for {
        props <- $.props
        state <- $.state
      } yield (props, state)).flatMap {
        case (props, state) if !state.isDisabled =>
          $.setState(state.copy(isDisabled = true)) >>
            Callback.future(Rpc.deleteRepository(props.slug).map {
              case Xor.Right(_) => props.ctl.set(DashboardRoute.Root)
              case Xor.Left(errs) =>
                $.modState(_.copy(isDisabled = false, error = Some(joinErrors(errs))))
            })
      }

    def updateConfirmationState(isOpen: Boolean): Callback =
      $.modState(_.copy(isConfirmationOpen = isOpen))

    def openDialog(e: ReactTouchEventH): Callback =
      updateConfirmationState(true)

    lazy val actions = js.Array(
      MuiFlatButton(key = "destroy", label = "Destroy", primary = true, onTouchTap = delete _)())

    lazy val helpBlock = <.label(
      ^.`for` := "delete",
      "Once you delete a repository, there is no going back. Please be certain.")

    def render(props: Props, state: State) = {
      val confirmationDialog = ConfirmationDialogComponent("Are you sure you want to delete this?",
                                                           actions,
                                                           isModal = false,
                                                           state.isConfirmationOpen,
                                                           updateConfirmationState _)
      val alertDialog: ReactNode = state.error match {
        case Some(error) =>
          AlertDialogComponent("An error occurred while trying to delete this repository",
                               isModal = false,
                               <.span(error))
        case _ => <.div()
      }
      <.div(<.h3("Delete this repository"),
            <.div(^.`class` := "row",
                  ^.key := "delete",
                  <.div(^.`class` := "col-xs-6",
                        MuiRaisedButton(`type` = "submit",
                                        secondary = true,
                                        label = "Delete",
                                        disabled = state.isDisabled,
                                        onTouchTap = openDialog _)()),
                  <.div(LayoutComponent.helpBlockClass, helpBlock)),
            alertDialog,
            confirmationDialog)
    }
  }

  private val component = ReactComponentB[Props]("DeleteRepository")
    .initialState(State(false, false, None))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String) =
    component.apply(Props(ctl, slug))
}
