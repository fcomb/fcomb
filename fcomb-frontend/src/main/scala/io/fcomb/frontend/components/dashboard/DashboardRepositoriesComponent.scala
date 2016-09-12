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

package io.fcomb.frontend.components.dashboard

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui.Mui.SvgIcons.ContentAdd
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.components.repository.{
  RepositoriesComponent,
  NamespaceComponent,
  Namespace
}
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.styles.Global
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.js
import scalacss.ScalaCssReact._

object DashboardRepositoriesComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(namespace: Option[Namespace],
                         isSearchActive: Boolean,
                         searchQuery: String)

  final case class Backend($ : BackendScope[Props, State]) {
    def setDefaultOwner(): Callback = {
      AppCircuit.currentUser match {
        case Some(p) =>
          val namespace = Namespace.User(p.username, Some(p.id))
          $.modState(_.copy(namespace = Some(namespace)))
        case _ => Callback.empty
      }
    }

    def updateNamespace(namespace: Namespace) =
      $.modState(_.copy(namespace = Some(namespace)))

    def setRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(route))

    def openSearch(e: ReactTouchEventH) =
      $.modState(_.copy(isSearchActive = true, searchQuery = ""))

    def closeSearch(e: ReactTouchEventH) =
      $.modState(_.copy(isSearchActive = false))

    lazy val searchButtonStyle =
      js.Dictionary("position" -> "absolute", "right" -> "8px", "top" -> "16px")

    def render(props: Props, state: State) = {
      val repositoriesSection = state.namespace match {
        case Some(namespace) =>
          val header: Seq[ReactElement] =
            if (state.isSearchActive)
              Seq(MuiTextField(hintText = "Name of repository to search", fullWidth = true)(),
                  <.div(^.style := searchButtonStyle,
                        MuiIconButton(onTouchTap = closeSearch _)(
                          Mui.SvgIcons.ContentClear(color = Mui.Styles.colors.lightBlack)())))
            else
              Seq(NamespaceComponent(namespace,
                                     isAdminRoleOnly = false,
                                     isAllNamespace = true,
                                     isDisabled = false,
                                     updateNamespace _),
                  <.div(^.style := searchButtonStyle,
                        MuiIconButton(onTouchTap = openSearch _)(
                          Mui.SvgIcons.ActionSearch(color = Mui.Styles.colors.lightBlack)())))
          MuiCard()(
            MuiCardTitle(key = "title")(header),
            MuiCardMedia(key = "repos")(RepositoriesComponent(props.ctl, namespace))
          )
        case _ => MuiCircularProgress(key = "progress")()
      }
      val route = state.namespace match {
        case Some(Namespace.Organization(slug, _)) =>
          DashboardRoute.NewOrganizationRepository(slug)
        case _ => DashboardRoute.NewRepository
      }
      <.section(<.div(Global.floatActionButton,
                      ^.title := "New repository",
                      MuiFloatingActionButton(secondary = true, onTouchTap = setRoute(route) _)(
                        ContentAdd()())),
                repositoriesSection)
    }
  }

  private val component = ReactComponentB[Props]("DashboardRepositories")
    .initialState(State(None, false, ""))
    .renderBackend[Backend]
    .componentWillMount(_.backend.setDefaultOwner())
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component.apply(Props(ctl))
}
