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

import io.fcomb.frontend.components.auth._
import io.fcomb.frontend.{DashboardRoute, Route}
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react._

object RouterComponent {
  val baseUrl = BaseUrl.fromWindowOrigin / "#"

  val routerConfig: RouterConfig[Route] = RouterConfigDsl[Route].buildConfig { dsl =>
    import dsl._

    val dashboardRoutes =
      DashboardComponent.routes.prefixPath_/("dashboard").pmap[Route](Route.Dashboard) {
        case Route.Dashboard(r) => r
      }

    val routes =
      // format: OFF
      trimSlashes |
      dashboardRoutes |
      staticRoute("sign_in", Route.SignIn) ~> renderR(AuthComponent.apply(_, AuthTab.SignIn)) |
      staticRoute("sign_up", Route.SignUp) ~> renderR(AuthComponent.apply(_, AuthTab.SignUp)) |
      staticRoute("sign_out", Route.SignOut) ~> renderR(SignOutComponent.apply(_))
      // format: ON

    routes
      .notFound(redirectToPage(Route.Dashboard(DashboardRoute.Root))(Redirect.Replace))
      .renderWith(LayoutComponent.apply)
  }

  private val component = Router(baseUrl, routerConfig.logToConsole)

  def apply(): ReactComponentU[Unit, Resolution[Route], Any, TopNode] =
    component.apply()
}
