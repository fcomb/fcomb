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

package io.fcomb.frontend.components.repository

import diode.data.{Empty, Failed, Pot, Ready}
import diode.react.ReactPot._
import cats.data.Xor
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Resource, Rpc, RpcMethod}
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.components.{PaginationOrderState, Table, ToolbarPaginationComponent}
import io.fcomb.frontend.styles.App
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.models.acl.{Action, MemberKind}
import io.fcomb.models.errors.ErrorsException
import io.fcomb.models.{OwnerKind, SortOrder}
import io.fcomb.rpc.acl._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scalacss.ScalaCssReact._

object RepositoryPermissionsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String, ownerKind: OwnerKind)
  final case class FormState(member: Option[PermissionMemberResponse],
                             action: Action,
                             errors: Map[String, String],
                             isFormDisabled: Boolean)
  final case class State(permissions: Pot[Seq[PermissionResponse]],
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
    FormState(None, Action.Read, Map.empty, false)

  final class Backend($ : BackendScope[Props, State]) {
    val limit = 25

    def getPermissions(pos: PaginationOrderState): Callback =
      $.modState(st => st.copy(permissions = st.permissions.pending())) >>
        $.props.flatMap { props =>
          Callback.future(
            Rpc
              .getRepositoryPermissions(props.slug, pos.sortColumn, pos.sortOrder, pos.page, limit)
              .map {
                case Xor.Right(pd) =>
                  $.modState(st =>
                    st.copy(permissions = Ready(pd.data),
                            pagination = st.pagination.copy(total = pd.total)))
                case Xor.Left(errs) =>
                  $.modState(_.copy(permissions = Failed(ErrorsException(errs))))
              })
        }

    def updatePermission(slug: String, name: String, kind: MemberKind)(e: ReactEventI) = {
      val action = Action.withName(e.target.value)
      e.preventDefaultCB >>
        $.state.flatMap { state =>
          $.setState(state.copy(form = state.form.copy(isFormDisabled = true))) >>
            Callback.future(upsertPermission(slug, name, kind, action).map {
              case Xor.Right(_) => updateFormDisabled(false) >> getPermissions(state.pagination)
              case Xor.Left(errs) =>
                $.setState(state.copy(
                  form = state.form.copy(isFormDisabled = false, errors = foldErrors(errs))))
            })
        }
    }

    lazy val actions = Action.values.map(r =>
      MuiMenuItem[Action](key = r.value, value = r, primaryText = r.entryName.capitalize)())

    lazy val memberKinds = MemberKind.values.map(k => <.option(^.value := k.value)(k.entryName))

    def renderActionCell(slug: String, state: State, permission: PermissionResponse) = {
      val member = permission.member
      if (member.isOwner) <.td(permission.action.toString())
      else
        <.td(
          <.select(^.disabled := state.form.isFormDisabled,
                   ^.required := true,
                   ^.value := permission.action.value,
                   ^.onChange ==> updatePermission(slug, member.name, member.kind),
                   actions))
    }

    def deletePermission(slug: String, name: String, kind: MemberKind)(e: ReactEventI) =
      e.preventDefaultCB >>
        $.state.flatMap { state => // TODO: DRY
          $.setState(state.copy(form = state.form.copy(isFormDisabled = true))) >>
            Callback.future(Rpc.deletRepositoryPermission(slug, name, kind).map {
              case Xor.Right(_) =>
                updateFormDisabled(false) >> getPermissions(state.pagination)
              case Xor.Left(e) => updateFormDisabled(false)
            })
        }

    // def renderPermissionRow(slug: String, state: State, permission: PermissionResponse) = {
    //   val member = permission.member
    //   if (member.isOwner) EmptyTag
    //   else {
    //     <.td(
    //       <.button(^.`type` := "button",
    //                ^.disabled := state.form.isFormDisabled,
    //                ^.onClick ==> deletePermission(slug, member.name, member.kind),
    //         "Delete"))

    // }

    def renderPermissionRow(slug: String, state: State, permission: PermissionResponse) = {
      // <.tr(<.td(permission.member.title),
      //      renderActionCell(slug, state, permission),
      //   renderOptionsCell(slug, state, permission))

      // val menuBtn =
      //   MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
      // val actions = Seq(
      //   CopyToClipboardComponent(
      //     dockerPullCommand,
      //     js.undefined,
      //     MuiMenuItem(primaryText = "Copy docker pull command", key = "copy")(),
      //     key = "copyDockerPull"),
      //   CopyToClipboardComponent(tag.digest,
      //     js.undefined,
      //     MuiMenuItem(primaryText = "Copy image digest", key = "copy")(),
      //     key = "copyDigest"))
      val title = permission.member.title
      MuiTableRow(key = title)(MuiTableRowColumn(key = "title")(title),
                               MuiTableRowColumn(key = "action")(permission.action.toString()),
                               MuiTableRowColumn(style = App.menuColumnStyle, key = "actions")())

      //         MuiIconMenu(iconButtonElement = menuBtn)(actions)
    }

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        state <- $.state.map(_.flipSortColumn(column))
        _     <- $.setState(state)
        _     <- getPermissions(state.pagination)
      } yield ()

    def updatePage(page: Int): Callback =
      $.state.flatMap { state =>
        val pagination = state.pagination.copy(page = page)
        $.setState(state.copy(pagination = pagination)) >> getPermissions(pagination)
      }

    def renderPermissions(slug: String, state: State) =
      state.permissions.renderReady { permissions =>
        if (permissions.isEmpty) <.div(App.infoMsg, "There are no permissions to show yet")
        else {
          val p = state.pagination
          val columns =
            MuiTableRow()(Table.renderHeader("User", "member.username", p, updateSort _),
                          Table.renderHeader("Action", "action", p, updateSort _),
                          MuiTableHeaderColumn(style = App.menuColumnStyle, key = "actions")())
          val rows = permissions.map(renderPermissionRow(slug, state, _))
          <.section(
            MuiTable(selectable = false, multiSelectable = false)(MuiTableHeader(
                                                                    adjustForCheckbox = false,
                                                                    displaySelectAll = false,
                                                                    enableSelectAll = false,
                                                                    key = "header"
                                                                  )(columns),
                                                                  MuiTableBody(
                                                                    deselectOnClickaway = false,
                                                                    displayRowCheckbox = false,
                                                                    showRowHover = false,
                                                                    stripedRows = false,
                                                                    key = "body"
                                                                  )(rows)),
            ToolbarPaginationComponent(p.page, limit, p.total, updatePage _))
        }
      }

    def updateFormDisabled(isFormDisabled: Boolean): Callback =
      $.modState(s => s.copy(form = s.form.copy(isFormDisabled = isFormDisabled)))

    private def upsertPermission(slug: String, name: String, kind: MemberKind, action: Action) = {
      val member = kind match {
        case MemberKind.User  => PermissionUsernameRequest(name)
        case MemberKind.Group => PermissionGroupNameRequest(name)
      }
      val req = PermissionCreateRequest(member, action)
      Rpc.callWith[PermissionCreateRequest, PermissionResponse](
        RpcMethod.PUT,
        Resource.repositoryPermissions(slug),
        req)
    }

    def add(props: Props): Callback =
      $.state.flatMap { state =>
        val fs = state.form
        fs.member match {
          case Some(member) if !fs.isFormDisabled =>
            $.setState(state.copy(form = fs.copy(isFormDisabled = true))) >>
              Callback.future(
                upsertPermission(props.slug, member.name, member.kind, fs.action).map {
                  case Xor.Right(_) =>
                    $.modState(_.copy(form = defaultFormState)) >> getPermissions(state.pagination)
                  case Xor.Left(errs) =>
                    $.setState(
                      state.copy(
                        form = state.form.copy(isFormDisabled = false, errors = foldErrors(errs))))
                })
          case _ => Callback.empty
        }
      }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback =
      e.preventDefaultCB >> add(props)

    def updateMember(member: PermissionMemberResponse): Callback =
      $.modState(s => s.copy(form = s.form.copy(member = Some(member))))

    def updateAction(e: ReactEventI, idx: Int, action: Action): Callback =
      $.modState(s => s.copy(form = s.form.copy(action = action)))

    def renderForm(props: Props, state: State) =
      <.form(^.onSubmit ==> handleOnSubmit(props),
             ^.disabled := state.form.isFormDisabled,
             <.div(^.display.flex,
                   ^.flexDirection.column,
                   MemberComponent.apply(props.slug,
                                         props.ownerKind,
                                         state.form.member.map(_.title),
                                         state.form.isFormDisabled,
                                         updateMember _),
                   MuiSelectField[Action](id = "action",
                                          floatingLabelText = "Action",
                                          errorText = state.form.errors.get("action"),
                                          value = state.form.action,
                                          onChange = updateAction _)(actions),
                   MuiRaisedButton(`type` = "submit",
                                   primary = true,
                                   label = "Add",
                                   disabled = state.form.isFormDisabled)()))

    def render(props: Props, state: State) =
      <.section(renderPermissions(props.slug, state), renderForm(props, state))
  }

  private val component = ReactComponentB[Props]("RepositoryPermissions")
    .initialState(State(Empty, PaginationOrderState("action", SortOrder.Desc), defaultFormState))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getPermissions($.state.pagination))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String, ownerKind: OwnerKind) =
    component.apply(Props(ctl, slug, ownerKind))
}
