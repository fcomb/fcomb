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
import scalacss.ScalaCssReact._

object DashboardRepositoriesComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(namespace: Option[Namespace])

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

    def render(props: Props, state: State) = {
      val repositoriesSection = state.namespace match {
        case Some(namespace) =>
          MuiCard()(
            MuiCardTitle(key = "title")(
              NamespaceComponent(namespace,
                                 isAdminRoleOnly = false,
                                 isAllNamespace = true,
                                 isDisabled = false,
                                 updateNamespace _)),
            MuiCardMedia(key = "repos")(RepositoriesComponent(props.ctl, namespace))
          )
        case _ => MuiCircularProgress(key = "progress")()
      }
      val route = state.namespace match {
        case Some(Namespace.Organization(slug, _)) =>
          DashboardRoute.NewOrganizationRepository(slug)
        case _ => DashboardRoute.NewRepository
      }
      <.section(<.h1("Repositories"),
                <.div(Global.floatActionButton,
                      ^.title := "New repository",
                      MuiFloatingActionButton(secondary = true, onTouchTap = setRoute(route) _)(
                        ContentAdd()())),
                repositoriesSection)
    }
  }

  private val component = ReactComponentB[Props]("DashboardRepositories")
    .initialState(State(None))
    .renderBackend[Backend]
    .componentWillMount(_.backend.setDefaultOwner())
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component.apply(Props(ctl))
}
