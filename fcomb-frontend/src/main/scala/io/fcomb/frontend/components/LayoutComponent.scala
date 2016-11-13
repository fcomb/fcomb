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

import cats.syntax.eq._
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.styles.App
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

    def setRoute(route: Route)(e: ReactEventH): Callback =
      closeDrawerCB() >> $.props.flatMap(_.ctl.set(route))

    def setRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      setRoute(Route.Dashboard(route))(e)

    lazy val copyrightBlock = <.footer(App.footer,
                                       <.a(App.footerLink,
                                           Seq(^.color := style.palette.primary3Color.toString),
                                           ^.href := "https://github.com/fcomb/fcomb",
                                           ^.target := "_blank",
                                           "© 2016 fcomb"))

    def renderRightMenu(page: Route) =
      page match {
        case Route.Dashboard(_) =>
          MuiIconMenu(iconButtonElement = MuiIconButton()(Mui.SvgIcons.NavigationMoreVert()()))(
            MuiMenuItem(primaryText = "Sign out",
                        key = "signout",
                        onTouchTap = setRoute(Route.SignOut) _)())
        case Route.SignIn =>
          MuiFlatButton(key = "signup", label = "Sign up", onTouchTap = setRoute(Route.SignUp) _)()
        case _ =>
          MuiFlatButton(key = "signin", label = "Sign in", onTouchTap = setRoute(Route.SignIn) _)()
      }

    def renderHeader(title: String, page: Route, isLogged: Boolean) =
      <.header(App.appBarHeader,
               MuiAppBar(title = title,
                         onLeftIconButtonTouchTap = openDrawer _,
                         iconElementRight = renderRightMenu(page),
                         showMenuIconButton = isLogged)())

    lazy val mainMenuItems = Seq(
      MuiSubheader(key = "my")("My"),
      MuiMenuItem(key = "repos",
                  primaryText = "Repositories",
                  onTouchTap = setRoute(DashboardRoute.Repositories) _)(),
      MuiMenuItem(key = "orgs",
                  primaryText = "Organizations",
                  onTouchTap = setRoute(DashboardRoute.Organizations) _)())

    lazy val settingsMenuItems = Seq(MuiSubheader(key = "settings")("Settings"),
                                     MuiMenuItem(key = "users",
                                                 primaryText = "Users",
                                                 onTouchTap =
                                                   setRoute(DashboardRoute.UsersSettings) _)())

    def renderDrawer(state: State) = {
      val menuItems =
        if (AppCircuit.currentUserIsAdmin) mainMenuItems ++ settingsMenuItems
        else mainMenuItems
      MuiDrawer(docked = false, open = state.isOpen, onRequestChange = closeDrawer _)(menuItems)
    }

    def render(props: Props, state: State) =
      sessionConn { sessionProxy =>
        val session  = sessionProxy.apply()
        val isLogged = session.nonEmpty
        val body = props.res.page match {
          case Route.Dashboard(_) =>
            if (isLogged) props.res.render()
            else signInRedirectComponent(props.ctl)
          case _ =>
            if (!isLogged || props.res.page === Route.SignOut) props.res.render()
            else dashboardRedirectComponent(props.ctl)
        }
        val (title, drawer) =
          if (isLogged) (props.res.page.title, Some(renderDrawer(state)))
          else ("fcomb", None)
        MuiMuiThemeProvider(muiTheme = theme)(
          <.div(renderHeader(title, props.res.page, isLogged),
                drawer,
                <.main(App.main, ^.`class` := "container", body),
                copyrightBlock))
      }
  }

  val style = Mui.Styles.LightRawTheme

  private val signInRedirectComponent = ReactComponentB[RouterCtl[Route]]("SignInRedirect")
    .render_P(_ => <.div("Unauthorized"))
    .componentDidMount(_.props.set(Route.SignIn).delayMs(1).void)
    .build

  private val dashboardRedirectComponent = ReactComponentB[RouterCtl[Route]]("DashboardRedirect")
    .render_P(_ => <.div("Authorized"))
    .componentDidMount(_.props.set(Route.Dashboard(DashboardRoute.Root)).delayMs(1).void)
    .build

  private val sessionConn = AppCircuit.connect(_.session)

  private val theme = Mui.Styles.getMuiTheme(style)

  lazy val linkAsTextStyle =
    Seq(^.textDecoration := "none", ^.color := style.palette.textColor.toString)

  lazy val linkStyle =
    Seq(^.textDecoration := "none", ^.color := style.palette.primary1Color.toString)

  lazy val helpBlockClass = (^.`class` := s"col-xs-6 ${App.helpBlock.htmlClass}")

  private val component =
    ReactComponentB[Props]("Layout").initialState(State(false)).renderBackend[Backend].build

  def apply(ctl: RouterCtl[Route], res: Resolution[Route]) = component(Props(ctl, res))
}
