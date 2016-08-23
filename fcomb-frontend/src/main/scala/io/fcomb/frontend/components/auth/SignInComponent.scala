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

package io.fcomb.frontend.components.auth

import cats.data.Xor
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.{DashboardRoute, Route}
import io.fcomb.frontend.services.AuthService
import io.fcomb.frontend.styles.Global
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object SignInComponent {
  final case class State(email: String, password: String, isFormDisabled: Boolean)

  class Backend($ : BackendScope[RouterCtl[Route], State]) {
    def authenticate(ctl: RouterCtl[Route]): Callback = {
      $.state.flatMap { state =>
        if (state.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
            Callback.future {
              AuthService
                .authentication(state.email, state.password)
                .map {
                  case Xor.Right(_) => ctl.set(Route.Dashboard(DashboardRoute.Root))
                  case Xor.Left(e)  => $.setState(state.copy(isFormDisabled = false))
                }
                .recover {
                  case _ => $.setState(state.copy(isFormDisabled = false))
                }
            }
          }
        }
      }
    }

    def handleOnSubmit(ctl: RouterCtl[Route])(e: ReactEventH): Callback = {
      e.preventDefaultCB >> authenticate(ctl)
    }

    def updateEmail(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(email = value))
    }

    def updatePassword(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(password = value))
    }

    def render(ctl: RouterCtl[Route], state: State) = {
      <.div(Global.app,
            <.h1("Sign In"),
            <.span("or"),
            <.div(ctl.link(Route.SignUp)("Sign Up")),
            <.form(^.onSubmit ==> handleOnSubmit(ctl),
                   ^.disabled := state.isFormDisabled,
                   <.div(
                     ^.display.flex,
                     ^.flexDirection.column,
                     MuiTextField(floatingLabelText = "Email",
                                  `type` = "email",
                                  id = "email",
                                  name = "email",
                                  disabled = state.isFormDisabled,
                                  value = state.email,
                                  onChange = updateEmail _)(),
                     MuiTextField(floatingLabelText = "Password",
                                  `type` = "password",
                                  id = "password",
                                  name = "password",
                                  disabled = state.isFormDisabled,
                                  value = state.password,
                                  onChange = updatePassword _)(),
                     MuiRaisedButton(`type` = "submit",
                                     primary = true,
                                     label = "Authenticate",
                                     disabled = state.isFormDisabled)())))
    }
  }

  private val component = ReactComponentB[RouterCtl[Route]]("SignIn")
    .initialState(State("", "", false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[Route]) = component(ctl)
}
