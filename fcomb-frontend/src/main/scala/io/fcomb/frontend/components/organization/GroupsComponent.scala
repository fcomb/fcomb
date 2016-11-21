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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import diode.data.{Empty, Failed, Pot, Ready}
import diode.react.ReactPot._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.organization.GroupForm._
import io.fcomb.frontend.components.{
  AlertDialogComponent,
  LayoutComponent,
  PaginationOrderState,
  TableComponent
}
import io.fcomb.frontend.DashboardRoute
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
  final case class State(groups: Pot[Seq[OrganizationGroupResponse]],
                         pagination: PaginationOrderState,
                         error: Option[String],
                         isDisabled: Boolean,
                         form: FormState) {
    def flipSortColumn(column: String): State = {
      val sortOrder =
        if (pagination.sortColumn == column) pagination.sortOrder.flip
        else pagination.sortOrder
      this.copy(pagination = pagination.copy(sortColumn = column, sortOrder = sortOrder))
    }
  }

  final class Backend($ : BackendScope[Props, State]) {
    val limit = 25

    def modFormState(f: FormState => FormState): Callback =
      $.modState(st => st.copy(form = f(st.form)))

    def tryAcquireState(f: State => Callback) =
      $.state.flatMap { state =>
        if (state.isDisabled) Callback.warn("State is already acquired")
        else
          $.modState(_.copy(isDisabled = true)) >> f(state).finallyRun(
            $.modState(_.copy(isDisabled = false)))
      }

    def getGroups(pos: PaginationOrderState): Callback =
      $.props.flatMap { props =>
        Callback.future(
          Rpc
            .getOrgaizationGroups(props.slug, pos.sortColumn, pos.sortOrder, pos.page, limit)
            .map {
              case Right(pd) =>
                $.modState(st =>
                  st.copy(groups = Ready(pd.data),
                          pagination = st.pagination.copy(total = pd.total)))
              case Left(errs) => $.modState(_.copy(groups = Failed(ErrorsException(errs))))
            })
      }

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        state <- $.state.map(_.flipSortColumn(column))
        _     <- $.modState(_.copy(pagination = state.pagination))
        _     <- getGroups(state.pagination)
      } yield ()

    def updatePage(page: Int): Callback =
      $.state.flatMap { state =>
        val pagination = state.pagination.copy(page = page)
        $.modState(_.copy(pagination = pagination)) >> getGroups(pagination)
      }

    def deleteGroup(slug: String, group: String)(e: ReactTouchEventH) =
      tryAcquireState { state =>
        Callback.future(Rpc.deleteOrganizationGroup(slug, group).map {
          case Right(_)   => getGroups(state.pagination)
          case Left(errs) => $.modState(_.copy(error = Some(joinErrors(errs))))
        })
      }

    def setRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(route))

    def renderGroup(props: Props, group: OrganizationGroupResponse, isDisabled: Boolean) = {
      val target = DashboardRoute.OrganizationGroup(props.slug, group.name)
      val menuBtn =
        MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
      val actions = Seq(
        MuiMenuItem(primaryText = "Open", key = "open", onTouchTap = setRoute(target) _)(),
        MuiMenuItem(primaryText = "Edit",
                    key = "edit",
                    onTouchTap =
                      setRoute(DashboardRoute.EditOrganizationGroup(props.slug, group.name)) _)(),
        MuiMenuItem(primaryText = "Delete",
                    key = "Delete",
                    onTouchTap = deleteGroup(props.slug, group.name) _)()
      )
      MuiTableRow(key = group.id.toString)(
        MuiTableRowColumn(key = "name")(
          props.ctl.link(target)(LayoutComponent.linkAsTextStyle, group.name)),
        MuiTableRowColumn(key = "role")(group.role.toString),
        MuiTableRowColumn(style = App.menuColumnStyle, key = "menu")(
          MuiIconMenu(iconButtonElement = menuBtn)(actions)
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

    def add(props: Props): Callback =
      tryAcquireState { state =>
        val fs = state.form
        Callback.future(Rpc.createOrganizationGroup(props.slug, fs.name, fs.role).map {
          case Right(_) =>
            $.modState(_.copy(form = defaultFormState)) >> getGroups(state.pagination)
          case Left(errs) => modFormState(_.copy(errors = foldErrors(errs)))
        })
      }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback =
      e.preventDefaultCB >> add(props)

    def updateName(name: String): Callback =
      modFormState(_.copy(name = name))

    def updateRole(role: Role): Callback =
      modFormState(_.copy(role = role))

    def renderFormButton(state: State) =
      <.div(^.style := App.paddingTopStyle,
            ^.key := "button",
            MuiRaisedButton(`type` = "submit",
                            primary = true,
                            label = "Add",
                            disabled = state.isDisabled)())

    def renderForm(props: Props, state: State) =
      <.form(App.separateBlock,
             ^.onSubmit ==> handleOnSubmit(props),
             ^.disabled := state.isDisabled,
             <.h3(^.style := App.formHeaderStyle, "New group"),
             renderFormName(state.form, state.isDisabled, updateName _),
             renderFormRole(state.form, state.isDisabled, updateRole _),
             renderFormButton(state))

    def render(props: Props, state: State) = {
      val alertDialog: ReactNode = state.error match {
        case Some(error) =>
          AlertDialogComponent("An error occurred while trying to delete this group",
                               isModal = false,
                               <.span(error))
        case _ => <.div()
      }
      <.section(
        alertDialog,
        state.groups.render(gs => renderGroups(props, gs, state.pagination, state.isDisabled)),
        renderForm(props, state))
    }
  }

  private val component = ReactComponentB[Props]("Groups")
    .initialState(
      State(Empty, PaginationOrderState("name"), None, isDisabled = false, defaultFormState))
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.getGroups($.state.pagination))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String) =
    component(Props(ctl, slug))
}
