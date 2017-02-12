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

package io.fcomb.frontend.components.auth

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.components.LayoutComponent
import io.fcomb.frontend.services.AuthService
import io.fcomb.frontend.styles.App
import io.fcomb.frontend.{DashboardRoute, Route}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

// TODO: check Config.security.isOpenSignUp
object SignUpComponent {
  final case class Props(ctl: RouterCtl[Route])
  final case class State(email: String,
                         password: String,
                         username: String,
                         fullName: String,
                         errors: Map[String, String],
                         isDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    def register(): Callback =
      $.state.flatMap { state =>
        if (state.isDisabled) Callback.empty
        else {
          $.modState(_.copy(isDisabled = true)) >>
            Callback
              .future {
                val email    = state.email.trim()
                val password = state.password.trim()
                val fullName = state.fullName.trim() match {
                  case s if s.nonEmpty => Some(s)
                  case _               => None
                }
                Rpc
                  .signUp(email, password, state.username.trim(), fullName)
                  .flatMap {
                    case Right(_)      => AuthService.authentication(email, password)
                    case res @ Left(_) => Future.successful(res)
                  }
                  .map {
                    case Right(_) =>
                      $.props.flatMap(_.ctl.set(Route.Dashboard(DashboardRoute.Root)))
                    case Left(errs) => $.modState(_.copy(errors = foldErrors(errs)))
                  }

              }
              .finallyRun($.modState(_.copy(isDisabled = false)))
        }
      }

    def handleOnSubmit(e: ReactEventH): Callback =
      e.preventDefaultCB >> register()

    def updateEmail(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(email = value))
    }

    def updatePassword(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(password = value))
    }

    def updateUsername(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(username = value))
    }

    def updateFullName(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(fullName = value))
    }

    def renderTextField(state: State,
                        value: String,
                        key: String,
                        label: String,
                        cb: ReactEventI => Callback,
                        `type`: String = "text") =
      <.div(
        ^.`class` := "row",
        ^.key := key,
        <.div(
          ^.`class` := "col-xs-12",
          MuiTextField(
            floatingLabelText = label,
            `type` = `type`,
            id = key,
            name = key,
            disabled = state.isDisabled,
            errorText = state.errors.get(key),
            fullWidth = true,
            value = value,
            onChange = cb
          )()
        )
      )

    def renderFormButtons(props: Props, state: State) =
      <.div(
        ^.`class` := "row",
        ^.style := App.paddingTopStyle,
        ^.key := "actionsRow",
        <.div(^.`class` := "col-xs-2",
              MuiRaisedButton(`type` = "submit",
                              primary = true,
                              label = "Create",
                              disabled = state.isDisabled)()),
        <.div(^.`class` := "col-xs-10",
              <.p(App.authRightTipBlock,
                  "Have an account? ",
                  props.ctl.link(Route.SignIn)(LayoutComponent.linkStyle, "Sign in"),
                  "."))
      )

    def renderForm(props: Props, state: State) =
      <.form(
        ^.onSubmit ==> handleOnSubmit,
        ^.disabled := state.isDisabled,
        ^.key := "form",
        MuiCardText(key = "form")(
          renderTextField(state, state.email, "email", "Email", updateEmail _, "email"),
          renderTextField(state,
                          state.password,
                          "password",
                          "Password",
                          updatePassword _,
                          "password"),
          renderTextField(state, state.username, "username", "Username", updateUsername _),
          renderTextField(state,
                          state.fullName,
                          "fullName",
                          "Full name (optional)",
                          updateFullName _),
          renderFormButtons(props, state)
        )
      )

    def render(props: Props, state: State) =
      <.div(
        ^.`class` := "row",
        <.div(
          ^.`class` := "col-xs-6 col-xs-offset-3",
          MuiCard()(
            <.div(^.key := "header",
                  App.formTitleBlock,
                  MuiCardTitle(key = "title")(<.h1(App.cardTitle, "Create your fcomb account"))),
            renderForm(props, state))
        )
      )
  }

  private val component = ReactComponentB[Props]("SignUp")
    .initialState(State("", "", "", "", Map.empty, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[Route]) = component(Props(ctl))
}
