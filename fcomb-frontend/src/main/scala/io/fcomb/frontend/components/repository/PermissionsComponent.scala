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
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.frontend.DashboardRoute
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.json.rpc.docker.distribution.Formats.decodeRepositoryResponse
import io.fcomb.models.acl.{Action, MemberKind}
import io.fcomb.models.OwnerKind
import io.fcomb.models.{PaginationData, SortOrder}
import io.fcomb.rpc.acl._
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object PermissionsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], repositoryName: String)
  final case class FormState(member: Option[PermissionMemberResponse],
                             action: Action,
                             isFormDisabled: Boolean)
  final case class State(permissions: Seq[PermissionResponse],
                         sortColumn: String,
                         sortOrder: SortOrder,
                         ownerKind: Option[OwnerKind],
                         form: FormState)

  private def defaultFormState =
    FormState(None, Action.Read, false)

  final class Backend($ : BackendScope[Props, State]) {
    // TODO: DRY with Diode Pot
    def getRepositoryOwnerKind(name: String): Callback = {
      Callback.future {
        Rpc.call[RepositoryResponse](RpcMethod.GET, Resource.repository(name)).map {
          case Xor.Right(repository) =>
            $.modState(_.copy(ownerKind = Some(repository.owner.kind)))
          case Xor.Left(e) => Callback.warn(e)
        }
      }
    }

    def getPermissions(name: String, sortColumn: String, sortOrder: SortOrder): Callback = {
      Callback.future {
        val queryParams = SortOrder.toQueryParams(Seq((sortColumn, sortOrder)))
        Rpc
          .call[PaginationData[PermissionResponse]](RpcMethod.GET,
                                                    Resource.repositoryPermissions(name),
                                                    queryParams = queryParams)
          .map {
            case Xor.Right(pd) =>
              $.modState(_.copy(permissions = pd.data))
            case Xor.Left(e) => Callback.warn(e)
          }
      }
    }

    def updatePermission(repositoryName: String, name: String, kind: MemberKind)(e: ReactEventI) = {
      val action = Action.withName(e.target.value)
      e.preventDefaultCB >>
        $.state.flatMap { state =>
          $.setState(state.copy(form = state.form.copy(isFormDisabled = true))) >>
            Callback.future {
              upsertPermission(repositoryName, name, kind, action).map {
                case Xor.Right(_) =>
                  updateFormDisabled(false) >>
                    getPermissions(repositoryName, state.sortColumn, state.sortOrder)
                case Xor.Left(e) =>
                  // TODO
                  updateFormDisabled(false)
              }.recover {
                case _ => updateFormDisabled(false)
              }
            }
        }
    }

    lazy val actions     = Action.values.map(k => <.option(^.value := k.value)(k.entryName))
    lazy val memberKinds = MemberKind.values.map(k => <.option(^.value := k.value)(k.entryName))

    def renderActionCell(repositoryName: String, state: State, permission: PermissionResponse) = {
      val member = permission.member
      if (member.isOwner) <.td(permission.action.toString())
      else
        <.td(
          <.select(^.disabled := state.form.isFormDisabled,
                   ^.required := true,
                   ^.value := permission.action.value,
                   ^.onChange ==> updatePermission(repositoryName, member.name, member.kind),
                   actions))
    }

    def deletePermission(repositoryName: String, name: String, kind: MemberKind)(e: ReactEventI) = {
      e.preventDefaultCB >>
        $.state.flatMap { state => // TODO: DRY
          $.setState(state.copy(form = state.form.copy(isFormDisabled = true))) >>
            Callback.future {
              val url = Resource.repositoryPermission(repositoryName, kind, name)
              Rpc
                .call[Unit](RpcMethod.DELETE, url)
                .map {
                  case Xor.Right(_) =>
                    updateFormDisabled(false) >>
                      getPermissions(repositoryName, state.sortColumn, state.sortOrder)
                  case Xor.Left(e) =>
                    // TODO
                    updateFormDisabled(false)
                }
                .recover {
                  case _ => updateFormDisabled(false)
                }
            }
        }
    }

    def renderOptionsCell(repositoryName: String, state: State, permission: PermissionResponse) = {
      val member = permission.member
      if (member.isOwner) EmptyTag
      else
        <.td(
          <.button(^.`type` := "button",
                   ^.disabled := state.form.isFormDisabled,
                   ^.onClick ==> deletePermission(repositoryName, member.name, member.kind),
                   "Delete"))
    }

    def renderPermissionRow(repositoryName: String, state: State, permission: PermissionResponse) = {
      <.tr(<.td(permission.member.title),
           renderActionCell(repositoryName, state, permission),
           renderOptionsCell(repositoryName, state, permission))
    }

    def changeSortOrder(column: String)(e: ReactEventH): Callback = {
      for {
        _     <- e.preventDefaultCB
        name  <- $.props.map(_.repositoryName)
        state <- $.state
        sortOrder = {
          if (state.sortColumn == column) state.sortOrder.flip
          else state.sortOrder
        }
        _ <- $.modState(_.copy(sortColumn = column, sortOrder = sortOrder))
        _ <- getPermissions(name, column, sortOrder)
      } yield ()
    }

    def renderHeader(title: String, column: String, state: State) = {
      val header = if (state.sortColumn == column) {
        val postfix = if (state.sortOrder === SortOrder.Asc) "↑" else "↓"
        s"$title $postfix"
      } else title
      <.th(<.a(^.href := "#", ^.onClick ==> changeSortOrder(column), header))
    }

    def renderPermissions(repositoryName: String, state: State) = {
      if (state.permissions.isEmpty) <.span("No permissions. Create one!")
      else {
        <.table(<.thead(
                  <.tr(renderHeader("User", "member.username", state),
                       renderHeader("Action", "action", state),
                       <.th())),
                <.tbody(state.permissions.map(renderPermissionRow(repositoryName, state, _))))
      }
    }

    def updateFormDisabled(isFormDisabled: Boolean): Callback = {
      $.modState(s => s.copy(form = s.form.copy(isFormDisabled = isFormDisabled)))
    }

    private def upsertPermission(repositoryName: String,
                                 name: String,
                                 kind: MemberKind,
                                 action: Action) = {
      val member = kind match {
        case MemberKind.User  => PermissionUsernameRequest(name)
        case MemberKind.Group => PermissionGroupNameRequest(name)
      }
      val req = PermissionCreateRequest(member, action)
      Rpc.callWith[PermissionCreateRequest, PermissionResponse](
        RpcMethod.PUT,
        Resource.repositoryPermissions(repositoryName),
        req)
    }

    def add(props: Props): Callback = {
      $.state.flatMap { state =>
        val fs = state.form
        fs.member match {
          case Some(member) if !fs.isFormDisabled =>
            $.setState(state.copy(form = fs.copy(isFormDisabled = true))) >>
              Callback.future {
                upsertPermission(props.repositoryName, member.name, member.kind, fs.action).map {
                  case Xor.Right(_) =>
                    $.modState(_.copy(form = defaultFormState)) >>
                      getPermissions(props.repositoryName, state.sortColumn, state.sortOrder)
                  case Xor.Left(e) =>
                    // TODO
                    updateFormDisabled(false)
                }.recover {
                  case _ => updateFormDisabled(false)
                }
              }
          case _ => Callback.empty
        }
      }
    }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback = {
      e.preventDefaultCB >> add(props)
    }

    def updateMember(member: PermissionMemberResponse): Callback = {
      $.modState(s => s.copy(form = s.form.copy(member = Some(member))))
    }

    def updateAction(e: ReactEventI): Callback = {
      val value = Action.withName(e.target.value)
      $.modState(s => s.copy(form = s.form.copy(action = value)))
    }

    def renderForm(props: Props, state: State) = {
      state.ownerKind match {
        case Some(ownerKind) =>
          <.form(^.onSubmit ==> handleOnSubmit(props),
                 ^.disabled := state.form.isFormDisabled,
                 MemberComponent.apply(props.repositoryName,
                                       ownerKind,
                                       state.form.member.map(_.title),
                                       state.form.isFormDisabled,
                                       updateMember _),
                 <.select(^.id := "action",
                          ^.name := "action",
                          ^.required := true,
                          ^.tabIndex := 2,
                          ^.value := state.form.action.value,
                          ^.onChange ==> updateAction,
                          actions),
                 <.input.submit(^.tabIndex := 3, ^.value := "Add"))
        case _ => EmptyTag // TODO
      }
    }

    def render(props: Props, state: State) = {
      <.div(<.h2("Permissions"),
            renderPermissions(props.repositoryName, state),
            <.hr,
            renderForm(props, state))
    }
  }

  private val component = ReactComponentB[Props]("Permissions")
    .initialState(State(Seq.empty, "action", SortOrder.Desc, None, defaultFormState))
    .renderBackend[Backend]
    .componentWillMount { $ =>
      for {
        _ <- $.backend.getRepositoryOwnerKind($.props.repositoryName)
        _ <- $.backend
          .getPermissions($.props.repositoryName, $.state.sortColumn, $.state.sortOrder)
      } yield ()
    }
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String) =
    component.apply(Props(ctl, repositoryName))
}
