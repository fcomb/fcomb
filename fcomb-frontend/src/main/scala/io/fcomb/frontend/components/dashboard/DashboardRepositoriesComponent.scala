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

import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.components.FloatActionButtonComponent
import io.fcomb.frontend.components.repository.{
  Namespace,
  NamespaceComponent,
  RepositoriesComponent
}
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.dispatcher.AppCircuit
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._

object DashboardRepositoriesComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(namespace: Option[Namespace])

  final class Backend($ : BackendScope[Props, State]) {
    def updateNamespace(namespace: Namespace) =
      $.state.flatMap {
        case state if !state.namespace.contains(namespace) =>
          $.modState(_.copy(namespace = Some(namespace)))
        case _ => Callback.empty
      }

    def render(props: Props, state: State) = {
      val repositoriesSection = state.namespace match {
        case Some(namespace) =>
          MuiCard()(MuiCardTitle(key = "title")(
                      NamespaceComponent(namespace,
                                         canCreateRoleOnly = false,
                                         isAllNamespace = true,
                                         isDisabled = false,
                                         isFullWidth = false,
                                         updateNamespace _)),
                    MuiCardText(key = "repos")(RepositoriesComponent(props.ctl, namespace)))
        case _ => MuiCircularProgress(key = "progress")()
      }
      val route = state.namespace match {
        case Some(Namespace.Organization(slug, _)) =>
          DashboardRoute.NewOrganizationRepository(slug)
        case _ => DashboardRoute.NewRepository
      }
      <.section(FloatActionButtonComponent(props.ctl, route, "New repository"),
                repositoriesSection)
    }
  }

  private def userNamespace() =
    AppCircuit.currentUser.map(u => Namespace.User(u.username, Some(u.id)))

  private val component = ReactComponentB[Props]("DashboardRepositories")
    .initialState(State(namespace = userNamespace()))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component.apply(Props(ctl))
}
