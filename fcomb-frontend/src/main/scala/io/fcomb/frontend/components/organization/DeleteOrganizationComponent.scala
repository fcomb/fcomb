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

import chandu0101.scalajs.react.components.Implicits._
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
import scala.util.{Left, Right}

object DeleteOrganizationComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String)
  final case class State(isValid: Boolean,
                         isDisabled: Boolean,
                         isConfirmationOpen: Boolean,
                         error: Option[String])

  final class Backend($ : BackendScope[Props, State]) {
    def delete(e: ReactTouchEventH): Callback =
      (for {
        props <- $.props
        state <- $.state
      } yield (props, state)).flatMap {
        case (props, state) if !state.isDisabled =>
          $.modState(_.copy(isDisabled = true)) >>
            Callback.future(Rpc.deleteOrganization(props.slug).map {
              case Right(_) => props.ctl.set(DashboardRoute.Organizations)
              case Left(errs) =>
                $.modState(_.copy(isDisabled = false, error = Some(joinErrors(errs))))
            })
      }

    def updateConfirmationState(isOpen: Boolean): Callback =
      $.modState(_.copy(isConfirmationOpen = isOpen))

    def openDialog(e: ReactTouchEventH): Callback =
      updateConfirmationState(true)

    def validateName(e: ReactEventI): Callback = {
      val value = e.target.value.trim()
      $.props.flatMap(props => $.modState(_.copy(isValid = props.slug == value)))
    }

    lazy val helpBlock = <.label(
      ^.`for` := "delete",
      "Once you delete an organization, there is no going back. It will be deleted forever. Please be certain.")

    def render(props: Props, state: State) = {
      val actions = js.Array(
        MuiFlatButton(key = "delete",
                      label = "Delete",
                      secondary = true,
                      disabled = !state.isValid,
                      onTouchTap = delete _)())
      val validateBlock = <.div(
        <.p(
          s"Please note that deleting this organization account will delete any and all repositories under the ",
          <.strong(props.slug),
          " account."),
        <.p("Enter this organization’s name to confirm"),
        MuiTextField(
          floatingLabelText = "Organization name",
          onChange = validateName _
        )())
      val confirmationDialog = ConfirmationDialogComponent("Are you sure you want to delete this?",
                                                           actions,
                                                           isModal = false,
                                                           state.isConfirmationOpen,
                                                           updateConfirmationState _,
                                                           validateBlock)
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

  private val component = ReactComponentB[Props]("DeleteOrganization")
    .initialState(State(isValid = false, isDisabled = false, isConfirmationOpen = false, None))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String) =
    component.apply(Props(ctl, slug))
}
