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

package io.fcomb.frontend.components.organization

import cats.data.Xor
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import diode.data.{Empty, Failed, Pot, Ready}
import diode.react.ReactPot._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.{
  FloatActionButtonComponent,
  LayoutComponent,
  PaginationOrderState,
  Table,
  ToolbarPaginationComponent
}
import io.fcomb.frontend.styles.App
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.PaginationData
import io.fcomb.rpc.OrganizationGroupResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object GroupsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String)
  final case class State(groups: Pot[Seq[OrganizationGroupResponse]],
                         pagination: PaginationOrderState)

  final class Backend($ : BackendScope[Props, State]) {
    val limit = 25

    def getGroups(pos: PaginationOrderState): Callback =
      $.props.flatMap { props =>
        Callback.future(
          Rpc
            .getOrgaizationGroups(props.slug, pos.sortColumn, pos.sortOrder, pos.page, limit)
            .map {
              case Xor.Right(pd) =>
                $.modState(st =>
                  st.copy(groups = Ready(pd.data),
                          pagination = st.pagination.copy(total = pd.total)))
              case Xor.Left(e) => Callback.warn(e)
            })
      }

    def deleteGroup(slug: String, group: String)(e: ReactEventI) =
      e.preventDefaultCB >>
        Callback.future(Rpc.deleteOrganizationGroup(slug, group).map {
          case Xor.Right(_) => $.state.flatMap(st => getGroups(st.pagination))
          case Xor.Left(e)  => ??? // TODO
        })

    def renderGroup(props: Props, group: OrganizationGroupResponse) =
      <.tr(
        <.td(props.ctl.link(DashboardRoute.OrganizationGroup(props.slug, group.name))(group.name)),
        <.td(
          <.button(^.`type` := "button",
                   ^.onClick ==> deleteGroup(props.slug, group.name),
                   "Delete")))

    def renderGroups(props: Props,
                     groups: Seq[OrganizationGroupResponse],
                     pos: PaginationOrderState) =
      if (groups.isEmpty) <.div(App.infoMsg, "There are no groups to show yet")
      else
        <.table(<.thead(<.tr(<.th("Username"), <.th("Email"), <.th())),
                <.tbody(groups.map(renderGroup(props, _))))

    def render(props: Props, state: State) =
      <.section(
        FloatActionButtonComponent(props.ctl,
                                   DashboardRoute.NewOrganizationGroup(props.slug),
                                   "New group"),
        MuiCard(key = "orgs")(MuiCardText(key = "orgs")(state.groups.render(gs =>
          renderGroups(props, gs, state.pagination))))
      )
  }

  private val component = ReactComponentB[Props]("Groups")
    .initialState(State(Empty, PaginationOrderState("name")))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getGroups($.state.pagination))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String) =
    component(Props(ctl, slug))
}
