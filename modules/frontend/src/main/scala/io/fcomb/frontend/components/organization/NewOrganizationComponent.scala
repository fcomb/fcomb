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

package io.fcomb.frontend.components.organization

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Form
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

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
                case Right(org) =>
                  $.props.flatMap(_.ctl.set(DashboardRoute.Organization(org.name)))
                case Left(errs) => $.modState(_.copy(errors = foldErrors(errs)))
              })
              .finallyRun($.modState(_.copy(isDisabled = false)))
        }
      }

    def handleOnSubmit(e: TouchTapEvent): Callback =
      e.preventDefaultCB >> create()

    def updateName(name: String): Callback =
      $.modState(_.copy(name = name))

    def renderFormName(state: State) =
      Form.row(
        Form.textField(label = "Name",
                       key = "name",
                       isDisabled = state.isDisabled,
                       errors = state.errors,
                       value = state.name,
                       onChange = updateName _),
        <.label(^.`for` := "name", "Unique organization name."),
        "name"
      )

    def cancel(e: TouchTapEvent): Callback =
      e.preventDefaultCB >> $.props.flatMap(_.ctl.set(DashboardRoute.Organizations))

    def renderFormButtons(state: State) =
      <.div(
        ^.`class` := "row",
        ^.style := App.paddingTopStyle,
        ^.key := "actionsRow",
        <.div(
          ^.`class` := "col-xs-12",
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
                          key = "update")()
        )
      )

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
