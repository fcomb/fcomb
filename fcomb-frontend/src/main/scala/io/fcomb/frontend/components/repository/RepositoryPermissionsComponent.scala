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

import cats.data.Xor
import cats.syntax.eq._
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Resource, Rpc, RpcMethod}
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.models.acl.{Action, MemberKind}
import io.fcomb.models.OwnerKind
import io.fcomb.models.SortOrder
import io.fcomb.rpc.acl._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object RepositoryPermissionsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String, ownerKind: OwnerKind)
  final case class FormState(member: Option[PermissionMemberResponse],
                             action: Action,
                             errors: Map[String, String],
                             isFormDisabled: Boolean)
  final case class State(permissions: Seq[PermissionResponse],
                         sortColumn: String,
                         sortOrder: SortOrder,
                         form: FormState)

  private def defaultFormState =
    FormState(None, Action.Read, Map.empty, false)

  final class Backend($ : BackendScope[Props, State]) {
    def getPermissions(slug: String, sortColumn: String, sortOrder: SortOrder): Callback =
      Callback.future(Rpc.getRepositoryPermissions(slug, sortColumn, sortOrder).map {
        case Xor.Right(pd) => $.modState(_.copy(permissions = pd.data))
        case Xor.Left(e)   => Callback.warn(e)
      })

    def updatePermission(slug: String, name: String, kind: MemberKind)(e: ReactEventI) = {
      val action = Action.withName(e.target.value)
      e.preventDefaultCB >>
        $.state.flatMap { state =>
          $.setState(state.copy(form = state.form.copy(isFormDisabled = true))) >>
            Callback.future {
              upsertPermission(slug, name, kind, action).map {
                case Xor.Right(_) =>
                  updateFormDisabled(false) >>
                    getPermissions(slug, state.sortColumn, state.sortOrder)
                case Xor.Left(errs) =>
                  $.setState(state.copy(
                    form = state.form.copy(isFormDisabled = false, errors = foldErrors(errs))))
              }.recover {
                case _ => updateFormDisabled(false)
              }
            }
        }
    }

    lazy val actions = Action.values.map(r =>
      MuiMenuItem[Action](key = r.value, value = r, primaryText = r.entryName)())
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
            Callback.future {
              val url = Resource.repositoryPermission(slug, kind, name)
              Rpc
                .call[Unit](RpcMethod.DELETE, url)
                .map {
                  case Xor.Right(_) =>
                    updateFormDisabled(false) >>
                      getPermissions(slug, state.sortColumn, state.sortOrder)
                  case Xor.Left(e) =>
                    // TODO
                    updateFormDisabled(false)
                }
                .recover {
                  case _ => updateFormDisabled(false)
                }
            }
        }

    def renderOptionsCell(slug: String, state: State, permission: PermissionResponse) = {
      val member = permission.member
      if (member.isOwner) EmptyTag
      else
        <.td(
          <.button(^.`type` := "button",
                   ^.disabled := state.form.isFormDisabled,
                   ^.onClick ==> deletePermission(slug, member.name, member.kind),
                   "Delete"))
    }

    def renderPermissionRow(slug: String, state: State, permission: PermissionResponse) =
      <.tr(<.td(permission.member.title),
           renderActionCell(slug, state, permission),
           renderOptionsCell(slug, state, permission))

    def changeSortOrder(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        name  <- $.props.map(_.slug)
        state <- $.state
        sortOrder = {
          if (state.sortColumn == column) state.sortOrder.flip
          else state.sortOrder
        }
        _ <- $.modState(_.copy(sortColumn = column, sortOrder = sortOrder))
        _ <- getPermissions(name, column, sortOrder)
      } yield ()

    def renderHeader(title: String, column: String, state: State) = {
      val header = if (state.sortColumn == column) {
        val postfix = if (state.sortOrder === SortOrder.Asc) "↑" else "↓"
        s"$title $postfix"
      } else title
      <.th(<.a(^.href := "#", ^.onClick ==> changeSortOrder(column), header))
    }

    def renderPermissions(slug: String, state: State) =
      if (state.permissions.isEmpty) <.span("No permissions. Create one!")
      else {
        <.table(<.thead(
                  <.tr(renderHeader("User", "member.username", state),
                       renderHeader("Action", "action", state),
                       <.th())),
                <.tbody(state.permissions.map(renderPermissionRow(slug, state, _))))
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
              Callback.future {
                upsertPermission(props.slug, member.name, member.kind, fs.action).map {
                  case Xor.Right(_) =>
                    $.modState(_.copy(form = defaultFormState)) >>
                      getPermissions(props.slug, state.sortColumn, state.sortOrder)
                  case Xor.Left(errs) =>
                    $.setState(state.copy(
                      form = state.form.copy(isFormDisabled = false, errors = foldErrors(errs))))
                }.recover {
                  case _ => updateFormDisabled(false)
                }
              }
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
      <.div(<.h2("Permissions"),
            renderPermissions(props.slug, state),
            <.hr,
            renderForm(props, state))
  }

  private val component = ReactComponentB[Props]("RepositoryPermissions")
    .initialState(State(Seq.empty, "action", SortOrder.Desc, defaultFormState))
    .renderBackend[Backend]
    .componentWillMount { $ =>
      $.backend.getPermissions($.props.slug, $.state.sortColumn, $.state.sortOrder)
    }
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String, ownerKind: OwnerKind) =
    component.apply(Props(ctl, slug, ownerKind))
}
