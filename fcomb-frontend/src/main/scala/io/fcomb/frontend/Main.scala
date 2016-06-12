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

package io.fcomb.frontend

import scala.scalajs.js
import js.JSApp
import org.scalajs.dom.document
import org.scalajs.dom.ext.Ajax
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scalacss.Defaults._
import scalacss.ScalaCssReact._
import chandu0101.scalajs.react.components.materialui._
import scalacss.mutable.GlobalRegistry
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Failure}

final case class Session(token: String)

final case class SessionCreateRequest(email: String, password: String)

object Formats {
  import upickle.default._

  final implicit val sessionPickler: Writer[Session] = macroW[Session]
  final implicit val sessionCreateRequestPickler: Writer[SessionCreateRequest] =
    macroW[SessionCreateRequest]
}
import Formats._

object Main extends JSApp {
  def main(): Unit = {
    GlobalRegistry.register(Styles)
    GlobalRegistry.addToDocumentOnRegistration()

    final case class AuthenticationFormState(
        email: String, password: String, isFormDisabled: Boolean)

    def getInitialState = AuthenticationFormState("", "", false)

    final case class AuthenticationFormBackend($ : BackendScope[Unit, AuthenticationFormState]) {
      def authenticate(e: ReactEventH): Callback = {
        $.state.flatMap { state =>
          if (state.isFormDisabled) Callback.empty
          else {
            val headers = Map("Content-Type" -> "application/json")
            val req = SessionCreateRequest(
              email = state.email.trim(),
              password = state.password.trim()
            )
            val resFut =
              Ajax.post("/api/v1/sessions", upickle.default.write(req), headers = headers)
            resFut.onComplete {
              case Success(req) =>
                println(s"res: ${req.response}")
                val json = upickle.json.read(req.responseText)
                println(s"json: $json")
                val session = upickle.default.readJs[Session](json)
                println(s"session: $session")
              case Failure(e) => println(s"error: $e")
            }
            $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
              Callback.future {
                resFut.map(_ => $.setState(getInitialState)).recover {
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

      def render(state: AuthenticationFormState) =
        <.div(Styles.container,
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
                     MuiRaisedButton(`type` = "submit",
                                     label = "Auth",
                                     primary = true)()))
    }

    val authenticationFormComponent = ReactComponentB[Unit]("AuthenticationForm")
      .initialState(getInitialState)
      .renderBackend[AuthenticationFormBackend]
      .build

    authenticationFormComponent().render(document.getElementById("app"))
  }
}

object Styles extends StyleSheet.Inline {
  import dsl._

  val button = style(
    addClassNames("btn", "btn-default")
  )

  val container = style(maxWidth(1024 px))

  val content = style(display.flex, padding(30.px), flexDirection.column, alignItems.center)
}
