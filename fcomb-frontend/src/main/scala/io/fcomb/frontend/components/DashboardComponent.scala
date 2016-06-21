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

import diode.react.ModelProxy
import io.fcomb.frontend.Route
import io.fcomb.frontend.styles.Global
import io.fcomb.frontend.components.dashboard.RepositoriesComponent
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scalacss.ScalaCssReact._

object DashboardComponent {
  final case class SessionState(ctl: RouterCtl[Route], session: ModelProxy[Option[String]])

  final case class Backend($ : BackendScope[SessionState, Unit]) {
    def render(sessionState: SessionState) = {
      val ctl = sessionState.ctl
      <.div(Global.app,
            <.h1("Dashboard"),
            <.div(ctl.link(Route.SignOut)("Sign Out")),
            <.hr,
            RepositoriesComponent.apply())
    }
  }

  private val component = ReactComponentB[SessionState]("DashboardComponent")
    .renderBackend[Backend]
    .componentDidMount { $ ⇒
      if ($.props.session().isEmpty) $.props.ctl.set(Route.SignIn).delayMs(1).void
      else Callback.empty
    }
    .build

  def apply(ctl: RouterCtl[Route], session: ModelProxy[Option[String]]) =
    component(SessionState(ctl, session))
}
