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
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.components.LayoutComponent
import io.fcomb.frontend.styles.App
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.util.{Right, Left}

object NewOrganizationComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(name: String, errors: Map[String, String], isDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    def create(): Callback =
      $.state.flatMap { state =>
        if (state.isDisabled) Callback.empty
        else {
          $.modState(_.copy(isDisabled = true)) >>
            Callback
              .future(Rpc.createOrganization(state.name).map {
                case Xor.Right(org) =>
                  $.props.flatMap(_.ctl.set(DashboardRoute.Organization(org.name)))
                case Xor.Left(errs) => $.modState(_.copy(errors = foldErrors(errs)))
              })
              .finallyRun($.modState(_.copy(isDisabled = false)))
        }
      }

    def handleOnSubmit(e: ReactEventH): Callback =
      e.preventDefaultCB >> create()

    def updateName(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(name = value))
    }

    def renderFormName(state: State) =
      <.div(^.`class` := "row",
            ^.key := "name",
            <.div(^.`class` := "col-xs-6",
                  MuiTextField(floatingLabelText = "Name",
                               id = "name",
                               name = "name",
                               disabled = state.isDisabled,
                               errorText = state.errors.get("name"),
                               fullWidth = true,
                               value = state.name,
                               onChange = updateName _)()),
            <.div(LayoutComponent.helpBlockClass,
                  ^.style := App.helpBlockStyle,
                  <.label(^.`for` := "name", "Unique organization name.")))

    def cancel(e: ReactEventH): Callback =
      e.preventDefaultCB >> $.props.flatMap(_.ctl.set(DashboardRoute.Organizations))

    def renderFormButtons(state: State) =
      <.div(^.`class` := "row",
            ^.style := App.paddingTopStyle,
            ^.key := "actionsRow",
            <.div(^.`class` := "col-xs-12",
                  MuiRaisedButton(`type` = "button",
                                  primary = false,
                                  label = "Cancel",
                                  style = App.cancelStyle,
                                  disabled = state.isDisabled,
                                  onTouchTap = cancel _,
                                  key = "cancel")(),
                  MuiRaisedButton(`type` = "submit",
                                  primary = true,
                                  label = "Create",
                                  disabled = state.isDisabled,
                                  key = "update")()))

    def renderForm(props: Props, state: State) =
      <.form(^.onSubmit ==> handleOnSubmit,
             ^.disabled := state.isDisabled,
             ^.key := "form",
             MuiCardText(key = "form")(renderFormName(state), renderFormButtons(state)))

    def render(props: Props, state: State) =
      MuiCard()(<.div(^.key := "header",
                      App.formTitleBlock,
                      MuiCardTitle(key = "title")(<.h1(App.cardTitle, "New organization"))),
                renderForm(props, state))
  }

  private val component = ReactComponentB[Props]("NewOrganization")
    .initialState(State("", Map.empty, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component(Props(ctl))
}
