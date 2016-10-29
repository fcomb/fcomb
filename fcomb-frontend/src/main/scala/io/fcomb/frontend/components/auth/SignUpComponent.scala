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
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.services.AuthService
import io.fcomb.frontend.{DashboardRoute, Route}
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

// TODO: check Config.security.isOpenSignUp
object SignUpComponent {
  final case class State(email: String,
                         password: String,
                         username: String,
                         fullName: String,
                         errors: Map[String, String],
                         isDisabled: Boolean)

  final class Backend($ : BackendScope[RouterCtl[Route], State]) {
    def register(ctl: RouterCtl[Route]): Callback =
      $.state.flatMap { state =>
        if (state.isDisabled) Callback.empty
        else {
          $.setState(state.copy(isDisabled = true)).flatMap { _ =>
            Callback.future {
              val email    = state.email.trim()
              val password = state.password.trim()
              val fullName = state.fullName.trim() match {
                case s if s.nonEmpty => Some(s)
                case _               => None
              }
              Rpc
                .signUp(email, password, state.username.trim(), fullName)
                .flatMap {
                  case Xor.Right(_)      => AuthService.authentication(email, password)
                  case res @ Xor.Left(_) => Future.successful(res)
                }
                .map {
                  case Xor.Right(_) => ctl.set(Route.Dashboard(DashboardRoute.Root))
                  case Xor.Left(errs) =>
                    $.setState(state.copy(isDisabled = false, errors = foldErrors(errs)))
                }
            }
          }
        }
      }

    def handleOnSubmit(ctl: RouterCtl[Route])(e: ReactEventH): Callback =
      e.preventDefaultCB >> register(ctl)

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

    def render(ctl: RouterCtl[Route], state: State) =
      <.form(^.onSubmit ==> handleOnSubmit(ctl),
             ^.disabled := state.isDisabled,
             <.div(^.display.flex,
                   ^.flexDirection.column,
                   MuiTextField(floatingLabelText = "Email",
                                `type` = "email",
                                id = "email",
                                name = "email",
                                disabled = state.isDisabled,
                                errorText = state.errors.get("email"),
                                value = state.email,
                                onChange = updateEmail _)(),
                   MuiTextField(floatingLabelText = "Password",
                                `type` = "password",
                                id = "password",
                                name = "password",
                                disabled = state.isDisabled,
                                errorText = state.errors.get("password"),
                                value = state.password,
                                onChange = updatePassword _)(),
                   MuiTextField(floatingLabelText = "Username",
                                id = "username",
                                name = "username",
                                disabled = state.isDisabled,
                                errorText = state.errors.get("username"),
                                value = state.username,
                                onChange = updateUsername _)(),
                   MuiTextField(floatingLabelText = "Full name (optional)",
                                id = "fullName",
                                name = "fullName",
                                disabled = state.isDisabled,
                                errorText = state.errors.get("fullName"),
                                value = state.fullName,
                                onChange = updateFullName _)(),
                   MuiRaisedButton(`type` = "submit",
                                   primary = true,
                                   label = "Register",
                                   disabled = state.isDisabled)()))
  }

  private val component = ReactComponentB[RouterCtl[Route]]("SignUp")
    .initialState(State("", "", "", "", Map.empty, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[Route]) = component(ctl)
}
