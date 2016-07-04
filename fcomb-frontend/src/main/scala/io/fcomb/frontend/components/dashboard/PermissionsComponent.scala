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

package io.fcomb.frontend.components.dashboard

import cats.data.Xor
import cats.syntax.eq._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.{PaginationData, SortOrder}
import io.fcomb.rpc.acl.{PermissionResponse, PermissionUserCreateRequest, PermissionUsernameRequest}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object PermissionsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], repositoryName: String)
  final case class FormState(username: String, action: Action, isFormDisabled: Boolean)
  final case class State(permissions: Seq[PermissionResponse],
                         sortColumn: String,
                         sortOrder: SortOrder,
                         form: FormState)

  private def defaultFormState =
    FormState("", Action.Read, false)

  final case class Backend($ : BackendScope[Props, State]) {
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
            case Xor.Left(e) =>
              println(e)
              Callback.empty
          }
      }
    }

    def updatePermission(repositoryName: String, username: String)(e: ReactEventI) = {
      val action = Action.withName(e.target.value)
      $.state.flatMap { state =>
        $.setState(state.copy(form = state.form.copy(isFormDisabled = true))) >>
        Callback.future {
          upsertPermissionRpc(repositoryName, username, action).map {
            case Xor.Right(repository) =>
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

    lazy val actions = Action.values.map(k => <.option(^.value := k.value)(k.entryName))

    def renderActionCell(repositoryName: String, state: State, permission: PermissionResponse) = {
      permission.member.username match {
        case Some(username) if !permission.member.isOwner =>
          <.td(
            <.select(^.disabled := state.form.isFormDisabled,
                     ^.required := true,
                     ^.value := permission.action.value,
                     ^.onChange ==> updatePermission(repositoryName, username),
                     actions))
        case _ => <.td(permission.action.toString())
      }
    }

    def renderPermissionRow(repositoryName: String, state: State, permission: PermissionResponse) = {
      <.tr(<.td(permission.member.username),
           renderActionCell(repositoryName, state, permission),
           <.td())
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
        if (state.sortOrder === SortOrder.Asc) s"$title ↑"
        else s"$title ↓"
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

    private def upsertPermissionRpc(repositoryName: String, username: String, action: Action) = {
      val member = PermissionUsernameRequest(username)
      val req    = PermissionUserCreateRequest(member, action)
      Rpc.callWith[PermissionUserCreateRequest, PermissionResponse](
        RpcMethod.PUT,
        Resource.repositoryPermissions(repositoryName),
        req)
    }

    def add(props: Props): Callback = {
      $.state.flatMap { state =>
        val fs = state.form
        if (fs.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(form = fs.copy(isFormDisabled = true))) >>
          Callback.future {
            upsertPermissionRpc(props.repositoryName, fs.username, fs.action).map {
              case Xor.Right(repository) =>
                $.modState(_.copy(form = defaultFormState)) >>
                  getPermissions(props.repositoryName, state.sortColumn, state.sortOrder)
              case Xor.Left(e) =>
                // TODO
                updateFormDisabled(false)
            }.recover {
              case _ => updateFormDisabled(false)
            }
          }
        }
      }
    }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback = {
      e.preventDefaultCB >> add(props)
    }

    def updateUsername(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(s => s.copy(form = s.form.copy(username = value)))
    }

    def updateAction(e: ReactEventI): Callback = {
      val value = Action.withName(e.target.value)
      $.modState(s => s.copy(form = s.form.copy(action = value)))
    }

    def renderForm(props: Props, state: State) = {
      <.form(^.onSubmit ==> handleOnSubmit(props),
             ^.disabled := state.form.isFormDisabled,
             <.input.text(^.id := "username",
                          ^.name := "username",
                          ^.autoFocus := true,
                          ^.required := true,
                          ^.tabIndex := 1,
                          ^.placeholder := "Username",
                          ^.value := state.form.username,
                          ^.onChange ==> updateUsername),
             <.select(^.id := "action",
                      ^.name := "action",
                      ^.required := true,
                      ^.tabIndex := 2,
                      ^.value := state.form.action.value,
                      ^.onChange ==> updateAction,
                      actions),
             <.input.submit(^.tabIndex := 3, ^.value := "Add"))
    }

    def render(props: Props, state: State) = {
      <.div(<.h2("Permissions"),
            renderPermissions(props.repositoryName, state),
            <.hr,
            renderForm(props, state))
    }
  }

  private val component = ReactComponentB[Props]("PermissionsComponent")
    .initialState(State(Seq.empty, "action", SortOrder.Desc, defaultFormState))
    .renderBackend[Backend]
    .componentWillMount { $ =>
      $.backend.getPermissions($.props.repositoryName, $.state.sortColumn, $.state.sortOrder)
    }
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String) =
    component.apply(Props(ctl, repositoryName))
}
