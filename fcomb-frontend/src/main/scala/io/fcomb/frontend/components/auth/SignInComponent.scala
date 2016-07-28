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
import io.fcomb.frontend.{DashboardRoute, Route}
import io.fcomb.frontend.dispatcher.AppCircuit
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
                   <.input.submit(^.tabIndex := 3, ^.value := "Authenticate")))
    }
  }

  private val component = ReactComponentB[RouterCtl[Route]]("SignInComponent")
    .initialState(State("", "", false))
    .renderBackend[Backend]
    .componentWillMount { $ ⇒
      if (AppCircuit.session.nonEmpty) $.props.set(Route.Dashboard(DashboardRoute.Root)).delayMs(1).void
      else Callback.empty
    }
    .build

  def apply(ctl: RouterCtl[Route]) = component(ctl)
}
