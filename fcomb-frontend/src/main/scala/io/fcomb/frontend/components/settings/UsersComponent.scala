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

package io.fcomb.frontend.components.settings

import diode.data.{Empty, Failed, Pot, Ready}
import diode.react.ReactPot._
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.{
  FloatActionButtonComponent,
  LayoutComponent,
  PaginationOrderState,
  TableComponent
}
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import io.fcomb.rpc.UserProfileResponse
import io.fcomb.models.errors.ErrorsException
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object UsersComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(users: Pot[Seq[UserProfileResponse]], pagination: PaginationOrderState) {
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
          $.modState(_.copy(pagination = pagination)) >> getUsers(pagination)
      }

    def setRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(route))

    def getUsers(pos: PaginationOrderState) =
      Callback.future(Rpc.getUsers(pos.sortColumn, pos.sortOrder, pos.page, limit).map {
        case Right(pd) =>
          $.modState(st =>
            st.copy(users = Ready(pd.data), pagination = st.pagination.copy(total = pd.total)))
        case Left(errs) => $.modState(_.copy(users = Failed(ErrorsException(errs))))
      })

    def renderUser(ctl: RouterCtl[DashboardRoute], user: UserProfileResponse) = {
      val menuBtn =
        MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
      val actions = Seq(
        // MuiMenuItem(primaryText = "Open", key = "open", onTouchTap = setRoute(target) _)()
      )
      val fullName = user.fullName.getOrElse("")
      MuiTableRow(key = user.id.toString)(
        MuiTableRowColumn(key = "username")(user.username),
        MuiTableRowColumn(key = "fullName")(fullName),
        MuiTableRowColumn(key = "email")(user.email),
        MuiTableRowColumn(key = "role")(user.role.toString),
        MuiTableRowColumn(style = App.menuColumnStyle, key = "menu")(
          MuiIconMenu(iconButtonElement = menuBtn)(actions)))
    }

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        state <- $.state.map(_.flipSortColumn(column))
        _     <- $.modState(_.copy(pagination = state.pagination))
        _     <- getUsers(state.pagination)
      } yield ()

    def renderUsers(props: Props, users: Seq[UserProfileResponse], p: PaginationOrderState) =
      if (users.isEmpty) <.div(App.infoMsg, "There are no users to show yet")
      else {
        val columns = Seq(TableComponent.header("Username", "username", p, updateSort _),
                          TableComponent.header("Full name", "fullName", p, updateSort _),
                          TableComponent.header("Email", "email", p, updateSort _),
                          TableComponent.header("Role", "role", p, updateSort _),
                          MuiTableHeaderColumn(style = App.menuColumnStyle, key = "menu")())
        val rows = users.map(renderUser(props.ctl, _))
        TableComponent(columns, rows, p.page, limit, p.total, updatePage _)
      }

    def render(props: Props, state: State): ReactElement =
      <.section(
        // FloatActionButtonComponent(props.ctl, DashboardRoute.NewOrganization, "New user"),
        MuiCard(key = "orgs")(MuiCardText(key = "users")(state.users.render(us =>
          renderUsers(props, us, state.pagination))))
      )
  }

  private val component =
    ReactComponentB[Props]("Users")
      .initialState(State(Empty, PaginationOrderState("username")))
      .renderBackend[Backend]
      .componentDidMount($ => $.backend.getUsers($.state.pagination))
      .build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component.apply(Props(ctl))
}
