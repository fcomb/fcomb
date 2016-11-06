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
  AlertDialogComponent,
  BreadcrumbsComponent,
  LayoutComponent,
  PaginationOrderState,
  TableComponent
}
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import io.fcomb.models.errors.ErrorsException
import io.fcomb.rpc.UserResponse
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object GroupComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String, group: String)
  final case class FormState(member: Option[UserResponse], errors: Map[String, String])
  final case class State(members: Pot[Seq[UserResponse]],
                         pagination: PaginationOrderState,
                         error: Option[String],
                         form: FormState,
                         isDisabled: Boolean) {
    def flipSortColumn(column: String): State = {
      val sortOrder =
        if (pagination.sortColumn == column) pagination.sortOrder.flip
        else pagination.sortOrder
      this.copy(pagination = pagination.copy(sortColumn = column, sortOrder = sortOrder))
    }
  }

  private def defaultFormState = FormState(None, Map.empty)

  final class Backend($ : BackendScope[Props, State]) {
    val limit = 25

    def getMembers(pos: PaginationOrderState) =
      $.props.flatMap { props =>
        Callback.future(
          Rpc
            .getOrgaizationGroupMembers(props.slug,
                                        props.group,
                                        pos.sortColumn,
                                        pos.sortOrder,
                                        pos.page,
                                        limit)
            .map {
              case Xor.Right(pd)  => $.modState(_.copy(members = Ready(pd.data)))
              case Xor.Left(errs) => $.modState(_.copy(members = Failed(ErrorsException(errs))))
            })
      }

    def modFormState(f: FormState => FormState): Callback =
      $.modState(st => st.copy(form = f(st.form)))

    def tryAcquireState(f: State => Callback) =
      $.state.flatMap { state =>
        if (state.isDisabled) Callback.warn("State is already acquired")
        else
          $.modState(_.copy(isDisabled = true)) >> f(state).finallyRun(
            $.modState(_.copy(isDisabled = false)))
      }

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        state <- $.state.map(_.flipSortColumn(column))
        _     <- $.modState(_.copy(pagination = state.pagination))
        _     <- getMembers(state.pagination)
      } yield ()

    def updatePage(page: Int): Callback =
      $.state.flatMap { state =>
        val pagination = state.pagination.copy(page = page)
        $.modState(_.copy(pagination = pagination)) >> getMembers(pagination)
      }

    def deleteMember(username: String)(e: ReactTouchEventH) =
      $.props.flatMap { props =>
        tryAcquireState { state =>
          Callback.future(
            Rpc.deleteOrganizationGroupMember(props.slug, props.group, username).map {
              case Xor.Right(_)   => getMembers(state.pagination)
              case Xor.Left(errs) => $.modState(_.copy(error = Some(joinErrors(errs))))
            })
        }
      }

    def renderMember(props: Props, member: UserResponse, isDisabled: Boolean) = {
      val button =
        MuiIconButton(disabled = isDisabled, onTouchTap = deleteMember(member.username) _)(
          Mui.SvgIcons.ActionDelete(color = Mui.Styles.colors.lightBlack)())
      val fullName: String = member.fullName.getOrElse("")
      MuiTableRow(key = member.id.toString)(
        MuiTableRowColumn(key = "username")(member.username),
        MuiTableRowColumn(key = "fullName")(fullName),
        MuiTableRowColumn(style = App.menuColumnStyle, key = "actions")(button))
    }

    def renderMembers(props: Props,
                      members: Seq[UserResponse],
                      p: PaginationOrderState,
                      isDisabled: Boolean) =
      if (members.isEmpty) <.div(App.infoMsg, "There are no members to show yet")
      else {
        val columns = Seq(TableComponent.header("User", "username", p, updateSort _),
                          TableComponent.header("Email", "email", p, updateSort _),
                          MuiTableHeaderColumn(style = App.menuColumnStyle, key = "actions")())
        val rows = members.map(renderMember(props, _, isDisabled))
        TableComponent(columns, rows, p.page, limit, p.total, updatePage _)
      }

    def add(props: Props): Callback =
      tryAcquireState { state =>
        state.form.member.map(_.username) match {
          case Some(username) =>
            Callback.future(
              Rpc.upsertOrganizationGroupMember(props.slug, props.group, username).map {
                case Xor.Right(_) =>
                  $.modState(_.copy(form = defaultFormState)) >> getMembers(state.pagination)
                case Xor.Left(errs) => modFormState(_.copy(errors = foldErrors(errs)))
              })
          case _ => Callback.empty
        }
      }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback =
      e.preventDefaultCB >> add(props)

    def updateMember(member: UserResponse): Callback =
      modFormState(_.copy(member = Some(member)))

    def renderFormUsername(props: Props, state: State) =
      <.div(^.`class` := "row",
            ^.key := "username",
            <.div(^.`class` := "col-xs-6",
                  UserMemberComponent(props.slug,
                                      props.group,
                                      state.form.member.map(_.title),
                                      state.isDisabled,
                                      state.form.errors.get("username"),
                                      isFullWidth = true,
                                      updateMember _)),
            <.div(LayoutComponent.helpBlockClass,
                  ^.style := App.helpBlockStyle,
                  <.label(^.`for` := "username", "Who will be added to this group.")))

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
             ^.key := "form",
             <.h3(^.style := App.formHeaderStyle, "New member"),
             renderFormUsername(props, state),
             renderFormButton(state))

    def setEditRoute(props: Props)(e: ReactEventH): Callback =
      props.ctl.set(DashboardRoute.EditOrganizationGroup(props.slug, props.group))

    def renderHeader(props: Props) = {
      val breadcrumbs = BreadcrumbsComponent(
        props.ctl,
        Seq((props.slug, DashboardRoute.Organization(props.slug)),
            ("Groups", DashboardRoute.OrganizationGroups(props.slug)),
            (props.group, DashboardRoute.OrganizationGroup(props.slug, props.group))))
      val menuIconBtn =
        MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
      val actions = Seq(
        MuiMenuItem(primaryText = "Edit", key = "edit", onTouchTap = setEditRoute(props) _)())
      val menu = MuiIconMenu(iconButtonElement = menuIconBtn)(actions)

      <.div(^.key := "header",
            App.cardTitleBlock,
            MuiCardTitle(key = "title")(
              <.div(^.`class` := "row",
                    ^.key := "title",
                    <.div(^.`class` := "col-xs-11", breadcrumbs),
                    <.div(^.`class` := "col-xs-1", <.div(App.rightActionBlock, menu)))))
    }

    def render(props: Props, state: State): ReactElement = {
      val alertDialog: ReactNode = state.error match {
        case Some(error) =>
          AlertDialogComponent("An error occurred while trying to delete this member",
                               isModal = false,
                               <.span(error))
        case _ => <.div()
      }
      <.section(alertDialog, state.members.render { members =>
        MuiCard(key = "repos")(renderHeader(props),
                               MuiCardText(key = "members")(
                                 renderMembers(props, members, state.pagination, state.isDisabled),
                                 renderForm(props, state)))
      })
    }
  }

  private val component = ReactComponentB[Props]("Group")
    .initialState(
      State(Empty, PaginationOrderState("username"), None, defaultFormState, isDisabled = false))
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.getMembers($.state.pagination))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String, name: String) =
    component(Props(ctl, slug, name))
}
