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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.styles.Global
import io.fcomb.frontend.{DashboardRoute, Route}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.prefix_<^._
import scalacss.ScalaCssReact._

object LayoutComponent {
  final case class Props(ctl: RouterCtl[Route], res: Resolution[Route])
  final case class State(isOpen: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    def openDrawer(e: ReactEventH): Callback =
      $.modState(s => s.copy(isOpen = !s.isOpen))

    def closeDrawerCB() =
      $.modState(_.copy(isOpen = false))

    def closeDrawer(open: Boolean, reason: String): Callback =
      closeDrawerCB()

    def setRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      closeDrawerCB() >> $.props.flatMap(_.ctl.set(Route.Dashboard(route)))

    def render(props: Props, state: State) = {
      val body = sessionConn { sessionProxy =>
        val session = sessionProxy.apply()
        props.res.page match {
          case Route.Dashboard(_) =>
            session match {
              case Some(session) => props.res.render()
              case _             => signInRedirectComponent.apply(props.ctl)
            }
          case _ =>
            if (session.isEmpty || props.res.page == Route.SignOut) props.res.render()
            else dashboardRedirectComponent.apply(props.ctl)
        }
      }
      MuiMuiThemeProvider(muiTheme = theme)(
        <.div(<.header(
                MuiAppBar(title = "fcomb registry",
                          onLeftIconButtonTouchTap = openDrawer _,
                          showMenuIconButton = true)()),
              MuiDrawer(docked = false, open = state.isOpen, onRequestChange = closeDrawer _)(
                MuiMenuItem(key = "repos",
                            primaryText = "Repositories",
                            onTouchTap = setRoute(DashboardRoute.Repositories) _)(),
                MuiMenuItem(key = "orgs",
                            primaryText = "Organizations",
                            onTouchTap = setRoute(DashboardRoute.Organizations) _)()
              ),
              <.main(^.`class` := "container", body),
              <.footer(Global.footer,
                       <.a(Global.footerLink,
                           Seq(^.color := style.palette.primary3Color.toString),
                           ^.href := "https://github.com/fcomb/fcomb",
                           ^.target := "_blank",
                           "© 2016 fcomb"))))
    }
  }

  val style = Mui.Styles.LightRawTheme

  private val signInRedirectComponent = ReactComponentB[RouterCtl[Route]]("SignInRedirect")
    .render_P(_ => <.div("Unauthorized"))
    .componentWillMount(_.props.set(Route.SignIn).delayMs(1).void)
    .build

  private val dashboardRedirectComponent = ReactComponentB[RouterCtl[Route]]("DashboardRedirect")
    .render_P(_ => <.div("Authorized"))
    .componentWillMount(_.props.set(Route.Dashboard(DashboardRoute.Root)).delayMs(1).void)
    .build

  private val sessionConn = AppCircuit.connect(_.session)

  private val theme = Mui.Styles.getMuiTheme(style)

  private val component =
    ReactComponentB[Props]("Layout").initialState(State(false)).renderBackend[Backend].build

  def apply(ctl: RouterCtl[Route], res: Resolution[Route]) = component(Props(ctl, res))
}
