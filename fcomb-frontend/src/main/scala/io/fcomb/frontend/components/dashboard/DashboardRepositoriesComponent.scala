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
  RepositoryOwner,
  OwnerComponent,
  OwnerItem
}
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.models.OwnerKind
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._

object DashboardRepositoriesComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(ownerItem: Option[OwnerItem])

  final case class Backend($ : BackendScope[Props, State]) {
    def setDefaultOwner(): Callback = {
      AppCircuit.currentUser match {
        case Some(p) =>
          val ownerItem = OwnerItem(p.id, OwnerKind.User, p.username)
          $.modState(_.copy(ownerItem = Some(ownerItem)))
        case _ => Callback.empty
      }
    }

    def updateOwnerItem(ownerItem: OwnerItem) =
      $.modState(_.copy(ownerItem = Some(ownerItem)))

    def setRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(route))

    def render(props: Props, state: State) = {
      val repositoriesSection = state.ownerItem match {
        case Some(ownerItem) =>
          val ownerScope = ownerItem.kind match {
            case OwnerKind.Organization =>
              RepositoryOwner.Organization(ownerItem.slug)
            case OwnerKind.User => RepositoryOwner.UserSelf
          }
          MuiCard()(MuiCardTitle(key = "title")(
                      OwnerComponent.apply(ownerScope, false, false, updateOwnerItem _)),
                    MuiCardText(key = "repos")(RepositoriesComponent.apply(props.ctl, ownerScope)))
        case _ => MuiCircularProgress(key = "progress")()
      }
      val route = state.ownerItem match {
        case Some(OwnerItem(id, OwnerKind.Organization, slug)) =>
          DashboardRoute.NewOrganizationRepository(slug)
        case _ => DashboardRoute.NewRepository
      }
      <.section(<.h1("Repositories"),
                MuiFloatingActionButton(onTouchTap = setRoute(route) _)(ContentAdd()()),
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
