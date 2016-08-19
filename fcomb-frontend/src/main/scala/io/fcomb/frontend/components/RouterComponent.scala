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

import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.{DashboardRoute, Route}
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._

object RouterComponent {
  val baseUrl = BaseUrl.fromWindowOrigin / "#"

  val routerConfig: RouterConfig[Route] = RouterConfigDsl[Route].buildConfig { dsl =>
    import dsl._

    val sessionConn = AppCircuit.connect(_.session)

    val dashboardRoutes =
      DashboardComponent.routes.prefixPath_/("dashboard").pmap[Route](Route.Dashboard) {
        case Route.Dashboard(r) => r
      }

    val routes =
      trimSlashes |
        dashboardRoutes |
        staticRoute("sign_in", Route.SignIn) ~> renderR(ctl => auth.SignInComponent.apply(ctl)) |
        staticRoute("sign_up", Route.SignUp) ~> renderR(ctl => auth.SignUpComponent.apply(ctl)) |
        staticRoute("sign_out", Route.SignOut) ~> renderR(ctl => auth.SignOutComponent.apply(ctl))

    routes
      .notFound(redirectToPage(Route.Dashboard(DashboardRoute.Root))(Redirect.Replace))
      .renderWith {
        case (ctl, res) =>
          val body = res.page match {
            case Route.Dashboard(_) =>
              sessionConn { sessionProxy =>
                sessionProxy.apply() match {
                  case Some(session) => DashboardComponent.apply(ctl, session, res)
                  case _             => signInRedirectComponent.apply(ctl)
                }
              }
            case _ => res.render()
          }
          MuiMuiThemeProvider(muiTheme = theme)(body)
      }
  }

  private val theme: MuiTheme = Mui.Styles.getMuiTheme(Mui.Styles.LightRawTheme)

  private val signInRedirectComponent = ReactComponentB[RouterCtl[Route]]("SignInRedirect")
    .render_P(_ => <.div("Unauthorized"))
    .componentWillMount(_.props.set(Route.SignIn).delayMs(1).void)
    .build

  private val component = Router(baseUrl, routerConfig.logToConsole)

  def apply(): ReactComponentU[Unit, Resolution[Route], Any, TopNode] =
    component.apply()
}
