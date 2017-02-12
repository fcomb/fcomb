/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.{
  AlertDialogComponent,
  ConfirmationDialogComponent,
  FloatActionButtonComponent,
  PaginationOrderState,
  TableComponent
}
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.styles.App
import io.fcomb.models.errors.ErrorsException
import io.fcomb.rpc.UserResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scalacss.ScalaCssReact._

object UsersComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(users: Pot[Seq[UserResponse]],
                         pagination: PaginationOrderState,
                         userDeleteConfirmation: Option[UserResponse],
                         isDisabled: Boolean,
                         error: Option[String]) {
    def flipSortColumn(column: String): State = {
      val sortOrder =
        if (pagination.sortColumn == column) pagination.sortOrder.flip
        else pagination.sortOrder
      this.copy(pagination = pagination.copy(sortColumn = column, sortOrder = sortOrder))
    }
  }

  final class Backend($ : BackendScope[Props, State]) {
    val limit = 25

    lazy val currentUserId = AppCircuit.currentUser.map(_.id)

    def tryAcquireState(f: State => Callback) =
      $.state.flatMap { state =>
        if (state.isDisabled) Callback.warn("State is already acquired")
        else
          $.modState(_.copy(isDisabled = true)) >> f(state).finallyRun(
            $.modState(_.copy(isDisabled = false)))
      }

    def updateDeleteConfirmation(user: Option[UserResponse]): Callback =
      $.modState(_.copy(userDeleteConfirmation = user))

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

    def confirmDelete(user: UserResponse)(e: ReactTouchEventH): Callback =
      updateDeleteConfirmation(Some(user))

    def renderUser(ctl: RouterCtl[DashboardRoute], user: UserResponse) = {
      val menuBtn =
        MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
      val defaultActions = Seq(
        MuiMenuItem(primaryText = "Edit",
                    key = "edit",
                    onTouchTap = setRoute(DashboardRoute.EditUser(user.username)) _)())
      val actions =
        if (currentUserId.contains(user.id)) defaultActions
        else
          defaultActions :+ MuiMenuItem(primaryText = "Delete",
                                        key = "delete",
                                        onTouchTap = confirmDelete(user) _)()
      val fullName = user.fullName.getOrElse("")
      MuiTableRow(key = user.id.toString)(
        MuiTableRowColumn(key = "username")(user.username),
        MuiTableRowColumn(key = "fullName")(fullName),
        MuiTableRowColumn(key = "email")(user.email),
        MuiTableRowColumn(key = "role")(user.role.toString),
        MuiTableRowColumn(style = App.menuColumnStyle, key = "menu")(
          MuiIconMenu(iconButtonElement = menuBtn)(actions))
      )
    }

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        state <- $.state.map(_.flipSortColumn(column))
        _     <- $.modState(_.copy(pagination = state.pagination))
        _     <- getUsers(state.pagination)
      } yield ()

    def renderUsers(props: Props, users: Seq[UserResponse], p: PaginationOrderState) =
      if (users.isEmpty) <.div(App.infoMsg, "There are no users to show yet")
      else {
        val columns = Seq(
          TableComponent.header("Username", "username", p, updateSort _),
          TableComponent.header("Full name", "fullName", p, updateSort _),
          TableComponent.header("Email", "email", p, updateSort _),
          TableComponent.header("Role", "role", p, updateSort _),
          MuiTableHeaderColumn(style = App.menuColumnStyle, key = "menu")()
        )
        val rows = users.map(renderUser(props.ctl, _))
        TableComponent(columns, rows, p.page, limit, p.total, updatePage _)
      }

    def deleteUser(e: ReactTouchEventH) =
      tryAcquireState { state =>
        state.userDeleteConfirmation match {
          case Some(user) =>
            Callback.future(Rpc.deleteUser(user.username).map {
              case Right(_)   => getUsers(state.pagination) >> updateDeleteConfirmation(None)
              case Left(errs) => $.modState(_.copy(error = Some(joinErrors(errs))))
            })
          case _ => Callback.empty
        }
      }

    def closeConfirmation(isOpen: Boolean) =
      updateDeleteConfirmation(None)

    lazy val actions = js.Array(
      MuiFlatButton(key = "destroy", label = "Destroy", primary = true, onTouchTap = deleteUser _)(
        ))

    def render(props: Props, state: State): ReactElement = {
      val confirmationDialog = ConfirmationDialogComponent("Are you sure you want to delete this?",
                                                           actions,
                                                           isModal = false,
                                                           state.userDeleteConfirmation.nonEmpty,
                                                           closeConfirmation _)
      val alertDialog: ReactNode = state.error match {
        case Some(error) =>
          AlertDialogComponent("An error occurred while trying to delete this user",
                               isModal = false,
                               <.span(error))
        case _ => <.div()
      }
      <.section(
        alertDialog,
        confirmationDialog,
        FloatActionButtonComponent(props.ctl, DashboardRoute.NewUser, "New user"),
        MuiCard(key = "orgs")(MuiCardText(key = "users")(state.users.render(us =>
          renderUsers(props, us, state.pagination))))
      )
    }
  }

  private val component =
    ReactComponentB[Props]("Users")
      .initialState(State(Empty, PaginationOrderState("username"), None, isDisabled = false, None))
      .renderBackend[Backend]
      .componentDidMount($ => $.backend.getUsers($.state.pagination))
      .build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component.apply(Props(ctl))
}
