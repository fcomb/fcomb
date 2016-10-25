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
import io.fcomb.frontend.components.{LayoutComponent, PaginationOrderState, TableComponent}
import io.fcomb.frontend.styles.App
import io.fcomb.models.acl.Role
import io.fcomb.models.errors.ErrorsException
import io.fcomb.rpc.OrganizationGroupResponse
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object GroupsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String)
  final case class FormState(name: String,
                             role: Role,
                             errors: Map[String, String],
                             isDisabled: Boolean)
  final case class State(groups: Pot[Seq[OrganizationGroupResponse]],
                         pagination: PaginationOrderState,
                         form: FormState) {
    def flipSortColumn(column: String): State = {
      val sortOrder =
        if (pagination.sortColumn == column) pagination.sortOrder.flip
        else pagination.sortOrder
      this.copy(pagination = pagination.copy(sortColumn = column, sortOrder = sortOrder))
    }
  }

  private def defaultFormState =
    FormState("", Role.Member, Map.empty, false)

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
              case Xor.Left(errs) => $.modState(_.copy(groups = Failed(ErrorsException(errs))))
            })
      }

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        state <- $.state.map(_.flipSortColumn(column))
        _     <- $.setState(state)
        _     <- getGroups(state.pagination)
      } yield ()

    def updatePage(page: Int): Callback =
      $.state.flatMap { state =>
        val pagination = state.pagination.copy(page = page)
        $.setState(state.copy(pagination = pagination)) >> getGroups(pagination)
      }

    def deleteGroup(slug: String, group: String)(e: ReactTouchEventH) =
      e.preventDefaultCB >>
        Callback.future(Rpc.deleteOrganizationGroup(slug, group).map {
          case Xor.Right(_) => $.state.flatMap(st => getGroups(st.pagination))
          case Xor.Left(e)  => Callback.warn(e)
        })

    def renderGroup(props: Props, group: OrganizationGroupResponse, isDisabled: Boolean) = {
      val target = DashboardRoute.OrganizationGroup(props.slug, group.name)
      MuiTableRow(key = group.id.toString)(
        MuiTableRowColumn(key = "name")(
          <.a(LayoutComponent.linkAsTextStyle,
              ^.href := props.ctl.urlFor(target).value,
              props.ctl.setOnLinkClick(target))(group.name)),
        MuiTableRowColumn(key = "role")(group.role.toString),
        MuiTableRowColumn(style = App.menuColumnStyle, key = "buttons")(
          MuiIconButton(disabled = isDisabled, onTouchTap = deleteGroup(props.slug, group.name) _)(
            Mui.SvgIcons.ActionDelete(color = Mui.Styles.colors.lightBlack)())
        ))
    }

    def renderGroups(props: Props,
                     groups: Seq[OrganizationGroupResponse],
                     p: PaginationOrderState,
                     isDisabled: Boolean) =
      if (groups.isEmpty) <.div(App.infoMsg, "There are no groups to show yet")
      else {
        val columns = Seq(TableComponent.header("Name", "name", p, updateSort _),
                          TableComponent.header("Role", "role", p, updateSort _),
                          MuiTableHeaderColumn(style = App.menuColumnStyle, key = "menu")())
        val rows = groups.map(renderGroup(props, _, isDisabled))
        TableComponent(columns, rows, p.page, limit, p.total, updatePage _)
      }

    def render(props: Props, state: State) =
      <.section(state.groups.render(gs =>
        renderGroups(props, gs, state.pagination, state.form.isDisabled)))
  }

  private val component = ReactComponentB[Props]("Groups")
    .initialState(State(Empty, PaginationOrderState("name"), defaultFormState))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getGroups($.state.pagination))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String) =
    component(Props(ctl, slug))
}
