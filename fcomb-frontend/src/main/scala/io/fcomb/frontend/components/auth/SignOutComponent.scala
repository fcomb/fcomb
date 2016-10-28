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

import io.fcomb.frontend.dispatcher.actions.LogOut
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.Route
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._

object SignOutComponent {
  final class Backend($ : BackendScope[RouterCtl[Route], Unit]) {
    def render(ctl: RouterCtl[Route]) =
      <.h1("sign out...")
  }

  private val component = ReactComponentB[RouterCtl[Route]]("SignOut")
    .renderBackend[Backend]
    .componentDidMount($ ⇒
      AppCircuit.dispatchCB(LogOut) >> $.props.set(Route.SignIn).delayMs(1).void)
    .build

  def apply(ctl: RouterCtl[Route]) = component(ctl)
}
