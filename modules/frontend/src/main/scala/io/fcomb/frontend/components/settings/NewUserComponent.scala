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

package io.fcomb.frontend.components.settings

import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.settings.UserForm._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import io.fcomb.frontend.utils.StringUtils
import io.fcomb.models.UserRole
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object NewUserComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(email: String,
                         password: String,
                         username: String,
                         fullName: String,
                         role: UserRole,
                         errors: Map[String, String],
                         isDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    def create(): Callback =
      $.state.flatMap { state =>
        if (state.isDisabled) Callback.empty
        else {
          $.modState(_.copy(isDisabled = true)) >>
            Callback
              .future(
                Rpc
                  .createUser(email = state.email,
                              password = state.password,
                              username = state.username,
                              StringUtils.trim(state.fullName),
                              state.role)
                  .map {
                    case Right(org) =>
                      $.props.flatMap(_.ctl.set(DashboardRoute.Users))
                    case Left(errs) => $.modState(_.copy(errors = foldErrors(errs)))
                  })
              .finallyRun($.modState(_.copy(isDisabled = false)))
        }
      }

    def handleOnSubmit(e: TouchTapEvent): Callback =
      e.preventDefaultCB >> create()

    def updateUsername(username: String): Callback =
      $.modState(_.copy(username = username))

    def updateEmail(email: String): Callback =
      $.modState(_.copy(email = email))

    def updatePassword(password: String): Callback =
      $.modState(_.copy(password = password))

    def updateFullName(fullName: String): Callback =
      $.modState(_.copy(fullName = fullName))

    def updateRole(role: UserRole): Callback =
      $.modState(_.copy(role = role))

    def renderForm(props: Props, state: State) =
      <.form(
        ^.onSubmit ==> handleOnSubmit,
        ^.disabled := state.isDisabled,
        ^.key := "form",
        MuiCardText(key = "form")(
          renderFormUsername(state.username, state.errors, state.isDisabled, updateUsername _),
          renderFormEmail(state.email, state.errors, state.isDisabled, updateEmail _),
          renderFormPassword(state.password, state.errors, state.isDisabled, updatePassword _),
          renderFormFullName(state.fullName, state.errors, state.isDisabled, updateFullName _),
          renderFormRole(state.role, state.errors, state.isDisabled, updateRole _),
          renderFormButtons(props.ctl, "Create", state.isDisabled)
        )
      )

    def render(props: Props, state: State) =
      MuiCard()(<.div(^.key := "header",
                      App.formTitleBlock,
                      MuiCardTitle(key = "title")(<.h1(App.cardTitle, "New user"))),
                renderForm(props, state))
  }

  private val component = ReactComponentB[Props]("NewUser")
    .initialState(State("", "", "", "", UserRole.User, Map.empty, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component(Props(ctl))
}
