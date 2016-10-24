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
import cats.syntax.eq._
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Resource, Rpc, RpcMethod}
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.components.{LayoutComponent, PaginationOrderState, Table}
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
import scalacss.ScalaCssReact._
import scala.scalajs.js

object RepositoryPermissionsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String, ownerKind: OwnerKind)
  final case class FormState(member: Option[PermissionMemberResponse],
                             action: Action,
                             errors: Map[String, String],
                             isDisabled: Boolean)
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

    def modFormState(f: FormState => FormState): Callback =
      $.modState(st => st.copy(form = f(st.form)))

    def tryAcquireState(f: State => Callback) =
      $.state.flatMap { state =>
        if (state.form.isDisabled) Callback.warn("State is already acquired")
        else
          $.setState(state.copy(form = state.form.copy(isDisabled = true))) >>
            f(state).finallyRun(modFormState(_.copy(isDisabled = false)))
      }

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

    def updatePermission(slug: String, name: String, kind: MemberKind, prevAction: Action)(
        e: ReactEventI,
        idx: Int,
        newAction: Action) =
      if (prevAction === newAction) Callback.empty
      else
        tryAcquireState { state =>
          Callback.future(upsertPermission(slug, name, kind, newAction).map {
            case Xor.Right(_)   => getPermissions(state.pagination)
            case Xor.Left(errs) => modFormState(_.copy(errors = foldErrors(errs)))
          })
        }

    lazy val actions = Action.values.map(r =>
      MuiMenuItem[Action](key = r.value, value = r, primaryText = r.entryName.capitalize)())

    lazy val memberKinds = MemberKind.values.map(k => <.option(^.value := k.value)(k.entryName))

    def renderActionCell(slug: String, isDisabled: Boolean, permission: PermissionResponse) = {
      val member = permission.member
      MuiSelectField[Action](
        value = permission.action,
        disabled = isDisabled,
        style = App.overflowHiddenStyle,
        onChange = updatePermission(slug, member.name, member.kind, permission.action) _)(actions)
    }

    def deletePermission(slug: String, name: String, kind: MemberKind)(e: ReactTouchEventH) =
      e.preventDefaultCB >>
        tryAcquireState { state =>
          Callback.future(Rpc.deletRepositoryPermission(slug, name, kind).map {
            case Xor.Right(_) => getPermissions(state.pagination)
            case Xor.Left(e)  => Callback.warn(e)
          })
        }

    def renderButtons(slug: String, isDisabled: Boolean, member: PermissionMemberResponse) =
      MuiIconButton(disabled = isDisabled,
                    onTouchTap = deletePermission(slug, member.name, member.kind) _)(
        Mui.SvgIcons.ActionDelete(color = Mui.Styles.colors.lightBlack)())

    def renderPermissionRow(slug: String, state: State, permission: PermissionResponse) = {
      val title = permission.member.title
      val memberTitle =
        if (permission.member.isOwner)
          <.label(^.title := "The owner of this repository", s"$title *")
        else <.label(title)
      val isDisabled = state.form.isDisabled || permission.member.isOwner
      MuiTableRow(key = title)(
        MuiTableRowColumn(key = "title")(memberTitle),
        MuiTableRowColumn(key = "action")(renderActionCell(slug, isDisabled, permission)),
        MuiTableRowColumn(style = App.menuColumnStyle, key = "buttons")(
          renderButtons(slug, isDisabled, permission.member)))
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

    def renderPermissions(slug: String, state: State, permissions: Seq[PermissionResponse]) =
      if (permissions.isEmpty) <.div(App.infoMsg, "There are no permissions to show yet")
      else {
        val p = state.pagination
        val columns =
          MuiTableRow()(Table.header("User", "member.username", p, updateSort _),
                        Table.header("Action", "action", p, updateSort _),
                        MuiTableHeaderColumn(style = App.menuColumnStyle, key = "actions")())
        val rows = permissions.map(renderPermissionRow(slug, state, _))
        Table(columns, rows, p.page, limit, p.total, updatePage _)
      }

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
      tryAcquireState { state =>
        val fs = state.form
        fs.member match {
          case Some(member) =>
            Callback.future(upsertPermission(props.slug, member.name, member.kind, fs.action).map {
              case Xor.Right(_) =>
                $.modState(_.copy(form = defaultFormState)) >> getPermissions(state.pagination)
              case Xor.Left(errs) => modFormState(_.copy(errors = foldErrors(errs)))
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

    def renderFormMember(props: Props, state: State) =
      <.div(^.`class` := "row",
            ^.key := "member",
            <.div(^.`class` := "col-xs-6",
                  MemberComponent(props.slug,
                                  props.ownerKind,
                                  state.form.member.map(_.title),
                                  state.form.isDisabled,
                                  state.form.errors.get("member"),
                                  isFullWidth = true,
                                  updateMember _)),
            <.div(LayoutComponent.helpBlockClass,
                  ^.style := App.helpBlockStyle,
                  <.label(^.`for` := "member", "Who will be added to this repository.")))

    lazy val actionHelpBlock =
      <.div("What can an user do with this repository:",
            <.ul(<.li(<.strong("Read"), " - pull only;"),
                 <.li(<.strong("Write"), " - pull and push;"),
                 <.li(<.strong("Manage"), " - pull, push and manage permissions.")))

    def renderFormAction(state: State) =
      <.div(^.`class` := "row",
            ^.key := "action",
            <.div(^.`class` := "col-xs-6",
                  MuiSelectField[Action](id = "action",
                                         floatingLabelText = "Action",
                                         errorText = state.form.errors.get("action"),
                                         value = state.form.action,
                                         fullWidth = true,
                                         onChange = updateAction _)(actions)),
            <.div(LayoutComponent.helpBlockClass,
                  ^.style := App.helpBlockStyle,
                  <.label(^.`for` := "action", actionHelpBlock)))

    def renderFormButton(state: State) = {
      val submitIsDisabled = state.form.isDisabled || state.form.member.isEmpty
      <.div(^.style := App.paddingTopStyle,
            ^.key := "button",
            MuiRaisedButton(`type` = "submit",
                            primary = true,
                            label = "Add",
                            disabled = submitIsDisabled)())
    }

    lazy val headerStyle = js.Dictionary("marginBottom" -> "0px")

    def renderForm(props: Props, state: State) =
      <.form(App.separateBlock,
             ^.onSubmit ==> handleOnSubmit(props),
             ^.disabled := state.form.isDisabled,
             <.h3(^.style := headerStyle, "New permission"),
             renderFormMember(props, state),
             renderFormAction(state),
             renderFormButton(state))

    def render(props: Props, state: State): ReactElement =
      <.section(state.permissions.render(ps => renderPermissions(props.slug, state, ps)),
                renderForm(props, state))
  }

  private val component = ReactComponentB[Props]("RepositoryPermissions")
    .initialState(State(Empty, PaginationOrderState("action", SortOrder.Desc), defaultFormState))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getPermissions($.state.pagination))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String, ownerKind: OwnerKind) =
    component.apply(Props(ctl, slug, ownerKind))
}
