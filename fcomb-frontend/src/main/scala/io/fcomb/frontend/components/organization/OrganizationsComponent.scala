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
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.{
  FloatActionButtonComponent,
  LayoutComponent,
  PaginationOrderState,
  TableComponent
}
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import io.fcomb.models.errors.ErrorsException
import io.fcomb.rpc.OrganizationResponse
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object OrganizationsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(orgs: Pot[Seq[OrganizationResponse]], pagination: PaginationOrderState) {
    def flipSortColumn(column: String): State = {
      val sortOrder =
        if (pagination.sortColumn == column) pagination.sortOrder.flip
        else pagination.sortOrder
      this.copy(pagination = pagination.copy(sortColumn = column, sortOrder = sortOrder))
    }
  }

  final class Backend($ : BackendScope[Props, State]) {
    val limit = 25

    def updatePage(page: Int): Callback =
      $.state.zip($.props).flatMap {
        case (state, props) =>
          val pagination = state.pagination.copy(page = page)
          $.setState(state.copy(pagination = pagination)) >> getOrgs(pagination)
      }

    def setRepositoryRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(route))

    def getOrgs(pos: PaginationOrderState) =
      Callback.future(Rpc.getOrganizations(pos.sortColumn, pos.sortOrder, pos.page, limit).map {
        case Xor.Right(pd) =>
          $.modState(st =>
            st.copy(orgs = Ready(pd.data), pagination = st.pagination.copy(total = pd.total)))
        case Xor.Left(errs) => $.modState(_.copy(orgs = Failed(ErrorsException(errs))))
      })

    def renderOrg(ctl: RouterCtl[DashboardRoute], org: OrganizationResponse) = {
      val target = DashboardRoute.Organization(org.name)
      val menuBtn =
        MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
      val actions = Seq(
        MuiMenuItem(primaryText = "Open",
                    key = "open",
                    onTouchTap = setRepositoryRoute(target) _)()
      )
      MuiTableRow(key = org.id.toString)(
        MuiTableRowColumn(key = "name")(
          <.a(LayoutComponent.linkAsTextStyle,
              ^.href := ctl.urlFor(target).value,
              ctl.setOnLinkClick(target))(org.name)),
        MuiTableRowColumn(style = App.menuColumnStyle, key = "menu")(
          MuiIconMenu(iconButtonElement = menuBtn)(actions)))
    }

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        state <- $.state.map(_.flipSortColumn(column))
        _     <- $.setState(state)
        _     <- getOrgs(state.pagination)
      } yield ()

    def renderOrgs(props: Props, orgs: Seq[OrganizationResponse], p: PaginationOrderState) =
      if (orgs.isEmpty) <.div(App.infoMsg, "There are no organizations to show yet")
      else {
        val columns = Seq(TableComponent.header("Name", "name", p, updateSort _),
                          MuiTableHeaderColumn(style = App.menuColumnStyle, key = "menu")())
        val rows = orgs.map(renderOrg(props.ctl, _))
        TableComponent(columns, rows, p.page, limit, p.total, updatePage _)
      }

    def render(props: Props, state: State): ReactElement =
      <.section(
        FloatActionButtonComponent(props.ctl, DashboardRoute.NewOrganization, "New organization"),
        MuiCard(key = "orgs")(MuiCardText(key = "orgs")(state.orgs.render(os =>
          renderOrgs(props, os, state.pagination))))
      )
  }

  private val component = ReactComponentB[Props]("Organizations")
    .initialState(State(Empty, PaginationOrderState("name")))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getOrgs($.state.pagination))
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component.apply(Props(ctl))
}
