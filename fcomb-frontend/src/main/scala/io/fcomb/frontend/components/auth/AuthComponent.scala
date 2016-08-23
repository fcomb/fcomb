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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.Route
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl

object AuthComponent {
  final case class Props(ctl: RouterCtl[Route], tab: AuthTab)

  final class Backend($ : BackendScope[Props, Unit]) {
    def onChange(tab: AuthTab, e: ReactEventH, el: ReactElement): Callback = {
      val route = tab match {
        case AuthTab.SignIn => Route.SignIn
        case AuthTab.SignUp => Route.SignUp
      }
      $.props.flatMap(_.ctl.set(route))
    }

    def render(props: Props) = {
      MuiTabs[AuthTab](value = props.tab, onChange = onChange _)(
        MuiTab(label = "Sign In", value = AuthTab.SignIn)(
          SignInComponent(props.ctl)
        ),
        MuiTab(label = "Sign Up", value = AuthTab.SignUp)(
          SignUpComponent(props.ctl)
        )
      )
    }
  }

  private val component = ReactComponentB[Props]("Auth").renderBackend[Backend].build

  def apply(ctl: RouterCtl[Route], tab: AuthTab) = component(Props(ctl, tab))
}
