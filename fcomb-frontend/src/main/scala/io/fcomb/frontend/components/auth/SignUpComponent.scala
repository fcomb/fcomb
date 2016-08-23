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
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.frontend.services.AuthService
import io.fcomb.rpc.UserSignUpRequest
import io.fcomb.json.rpc.Formats.encodeUserSignUpRequest
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

// TODO: check Config.security.isOpenSignUp
object SignUpComponent {
  final case class State(email: String,
                         password: String,
                         username: String,
                         fullName: String,
                         isFormDisabled: Boolean)

  class Backend($ : BackendScope[RouterCtl[Route], State]) {
    def register(ctl: RouterCtl[Route]): Callback = {
      $.state.flatMap { state =>
        if (state.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
            Callback.future {
              val email    = state.email.trim()
              val password = state.password.trim()
              val fullName = state.fullName.trim() match {
                case s if s.nonEmpty => Some(s)
                case _               => None
              }
              val req = UserSignUpRequest(
                email = email,
                password = password,
                username = state.username.trim(),
                fullName = fullName
              )
              Rpc
                .callWith[UserSignUpRequest, Unit](RpcMethod.POST, Resource.signUp, req)
                .flatMap {
                  case Xor.Right(_)      => AuthService.authentication(email, password)
                  case res @ Xor.Left(e) => Future.successful(res)
                }
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
      e.preventDefaultCB >> register(ctl)
    }

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

    def render(ctl: RouterCtl[Route], state: State) = {
      <.form(^.onSubmit ==> handleOnSubmit(ctl),
             ^.disabled := state.isFormDisabled,
             <.div(^.display.flex,
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
                   MuiTextField(floatingLabelText = "Username",
                                id = "username",
                                name = "username",
                                disabled = state.isFormDisabled,
                                value = state.username,
                                onChange = updateUsername _)(),
                   MuiTextField(floatingLabelText = "Full name (optional)",
                                id = "fullName",
                                name = "username",
                                disabled = state.isFormDisabled,
                                value = state.fullName,
                                onChange = updateFullName _)(),
                   MuiRaisedButton(`type` = "submit",
                                   primary = true,
                                   label = "Register",
                                   disabled = state.isFormDisabled)()))
    }
  }

  private val component = ReactComponentB[RouterCtl[Route]]("SignUp")
    .initialState(State("", "", "", "", false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[Route]) = component(ctl)
}
