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
import io.fcomb.frontend.{DashboardRoute, Route}
import io.fcomb.frontend.styles.Global
import io.fcomb.frontend.components.dashboard._
import scala.scalajs.js
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.prefix_<^._
import scalacss.ScalaCssReact._

object DashboardComponent {
  val routes = RouterConfigDsl[DashboardRoute].buildRule { dsl =>
    import dsl._

    val repositoryNameFormat = """[\w-]+/[\w-]+"""

    trimSlashes |
    staticRoute(root, DashboardRoute.Root) ~> redirectToPage(DashboardRoute.Repositories)(
      Redirect.Replace) |
    staticRoute("repositories", DashboardRoute.Repositories) ~> renderR(
      ctl => RepositoriesComponent.apply(ctl)) |
    staticRoute("repositories" / "new", DashboardRoute.NewRepository) ~> renderR(
      ctl => NewRepositoryComponent.apply(ctl)) |
    dynamicRouteCT("repositories" ~ ("/" ~ string(repositoryNameFormat)).caseClass[DashboardRoute.Repository]) ~> dynRenderR((r, ctl) => RepositoryComponent.apply(ctl, r.name))
  }

  final case class State(ctl: RouterCtl[Route],
                         session: ModelProxy[Option[String]],
                         res: Resolution[Route])

  final case class Backend($ : BackendScope[State, Unit]) {
    def render(state: State) = {
      <.div(Global.app,
        <.header(
          <.h1("Dashboard"),
          <.ul(
            <.li(state.ctl.link(Route.Dashboard(DashboardRoute.Repositories))("Repositories")),
            <.li(state.ctl.link(Route.Dashboard(DashboardRoute.NewRepository))("New repository"))),
            <.div(state.ctl.link(Route.SignOut)("Sign Out"))),
            <.hr(
              ^.style := js
                .Dictionary("borderStyle" -> "solid", "borderColor" -> "red", "width" -> "100%")),
            state.res.render())
    }
  }

  private val component = ReactComponentB[State]("DashboardComponent")
    .renderBackend[Backend]
    .componentDidMount { $ ⇒
      if ($.props.session().isEmpty) $.props.ctl.set(Route.SignIn).delayMs(1).void
      else Callback.empty
    }
    .build

  def apply(ctl: RouterCtl[Route], session: ModelProxy[Option[String]], res: Resolution[Route]) =
    component(State(ctl, session, res))
}
