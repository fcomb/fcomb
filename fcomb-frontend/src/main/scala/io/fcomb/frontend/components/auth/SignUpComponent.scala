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
import io.fcomb.frontend.Route
import io.fcomb.frontend.api._, Formats._
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.services.AuthService
import io.fcomb.frontend.styles.Global
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.concurrent.Future
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object SignUpComponent {
  final case class State(
      email: String, password: String, username: String, fullName: String, isFormDisabled: Boolean)

  final case class Backend($ : BackendScope[RouterCtl[Route], State]) {
    def register(ctl: RouterCtl[Route]): Callback = {
      $.state.flatMap { state =>
        if (state.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
            Callback.future {
              val email    = state.email.trim()
              val password = state.password.trim()
              val req = UserSignUpRequest(
                email = email,
                password = password,
                username = state.username.trim(),
                fullName = state.fullName
              )
              Rpc
                .callWith[UserSignUpRequest, Unit](RpcMethod.POST, "/api/v1/users/sign_up", req)
                .flatMap {
                  case Xor.Right(_)      => AuthService.authentication(email, password)
                  case res @ Xor.Left(e) => Future.successful(res)
                }
                .map {
                  case Xor.Right(_) => ctl.set(Route.Dashboard)
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
      <.div(Global.app,
            <.h1("Sign Up"),
            <.span("or"),
            <.div(ctl.link(Route.SignIn)("Sign In")),
            <.form(^.onSubmit ==> handleOnSubmit(ctl),
                   ^.disabled := state.isFormDisabled,
                   <.label(^.`for` := "email", "Email"),
                   <.input.email(^.id := "email",
                                 ^.name := "email",
                                 ^.autoComplete := "home email",
                                 ^.autoFocus := true,
                                 ^.required := true,
                                 ^.tabIndex := 1,
                                 ^.value := state.email,
                                 ^.onChange ==> updateEmail),
                   <.br,
                   <.label(^.`for` := "password", "Password"),
                   <.input.password(^.id := "password",
                                    ^.name := "password",
                                    ^.autoComplete := "password",
                                    ^.required := true,
                                    ^.tabIndex := 2,
                                    ^.value := state.password,
                                    ^.onChange ==> updatePassword),
                   <.br,
                   <.label(^.`for` := "username", "Username"),
                   <.input.text(^.id := "username",
                                ^.name := "username",
                                ^.autoComplete := "username",
                                ^.required := true,
                                ^.tabIndex := 3,
                                ^.value := state.username,
                                ^.onChange ==> updateUsername),
                   <.br,
                   <.label(^.`for` := "fullName", "Full name (optional)"),
                   <.input.text(^.id := "fullName",
                                ^.name := "fullName",
                                ^.autoComplete := "full-name",
                                ^.tabIndex := 4,
                                ^.value := state.fullName,
                                ^.onChange ==> updateFullName),
                   <.br,
                   <.input.submit(^.tabIndex := 5, ^.value := "Register")))
    }
  }

  private val component = ReactComponentB[RouterCtl[Route]]("SignUpComponent")
    .initialState(State("", "", "", "", false))
    .renderBackend[Backend]
    .componentDidMount { $ ⇒
      if (AppCircuit.session.nonEmpty) $.props.set(Route.Dashboard).delayMs(1).void
      else Callback.empty
    }
    .build

  def apply(ctl: RouterCtl[Route]) = component(ctl)
}
