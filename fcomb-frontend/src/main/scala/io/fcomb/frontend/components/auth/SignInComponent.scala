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

package io.fcomb.frontend.components

import cats.data.Xor
import io.fcomb.frontend.api._, Formats._
import io.fcomb.frontend.styles._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.window
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object SignInComponent {
  final case class State(email: String, password: String, isFormDisabled: Boolean)

  def getInitialState = State("i@fcomb.io", "qwerty", false)

  final case class Backend($ : BackendScope[Unit, State]) {
    def authenticate(e: ReactEventH): Callback = {
      $.state.flatMap { state =>
        if (state.isFormDisabled) Callback.empty
        else {
          val req = SessionCreateRequest(
            email = state.email.trim(),
            password = state.password.trim()
          )
          val resFut =
            Rpc.call[SessionCreateRequest, Session](RpcMethod.POST, "/api/v1/sessions", req)
          resFut.foreach {
            case Xor.Right(session) =>
              println(s"session: $session")
              window.localStorage.setItem(sessionKey, session.token)
            case Xor.Left(e) => println(s"error: $e")
          }
          $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
            Callback.future {
              resFut.map {
                case Xor.Right(_) => $.setState(getInitialState)
                case Xor.Left(e)  => $.setState(state.copy(isFormDisabled = false))
              }.recover {
                case _ => $.setState(state.copy(isFormDisabled = false))
              }
            }
          }
        }
      }
    }

    def handleOnSubmit(e: ReactEventH): Callback = {
      e.preventDefaultCB >> authenticate(e)
    }

    def updateEmail(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(email = value))
    }

    def updatePassword(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(password = value))
    }

    def render(state: State) = {
      <.div(Global.app,
            getToken().map(token => <.h4(s"token: $token")),
            <.form(^.onSubmit ==> handleOnSubmit,
                   ^.disabled := state.isFormDisabled,
                   <.label(^.`for` := "email", "Email"),
                   <.input.email(^.id := "email",
                                 ^.name := "email",
                                 ^.autoComplete := "home email",
                                 ^.autoFocus := true,
                                 ^.value := state.email,
                                 ^.onChange ==> updateEmail),
                   <.label(^.`for` := "password", "Password"),
                   <.input.password(^.id := "password",
                                    ^.name := "password",
                                    ^.autoComplete := "password",
                                    ^.value := state.password,
                                    ^.onChange ==> updatePassword),
                   <.input.submit(^.value := "Auth")))
    }
  }

  def getToken(): Option[String] = {
    val key = window.localStorage.getItem(sessionKey)
    if (key.isEmpty) None else Some(key)
  }

  private val sessionKey = "sessionToken"

  val component = ReactComponentB[Unit]("SignInComponent")
    .initialState(getInitialState)
    .renderBackend[Backend]
    .build
}
