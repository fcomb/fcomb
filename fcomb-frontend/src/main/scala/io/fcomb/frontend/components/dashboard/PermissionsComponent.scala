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
import io.fcomb.models.{PaginationData, SortOrder}
import io.fcomb.rpc.acl.PermissionResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object PermissionsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], repositoryName: String)
  final case class State(permissions: Seq[PermissionResponse],
                         sortColumn: String,
                         sortOrder: SortOrder)

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

    def renderPermissionRow(props: Props, permission: PermissionResponse) = {
      <.tr(<.td(permission.member.username), <.td(permission.action.toString()), <.td())
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

    def renderPermissions(props: Props, state: State) = {
      if (state.permissions.isEmpty) <.span("No permissions. Create one!")
      else {
        <.table(<.thead(
                  <.tr(renderHeader("User", "member.id", state),
                       renderHeader("Action", "action", state),
                       <.th())),
                <.tbody(state.permissions.map(renderPermissionRow(props, _))))
      }
    }

    def render(props: Props, state: State) = {
      <.div(<.h2("Permissions"), renderPermissions(props, state))
    }
  }

  private val component = ReactComponentB[Props]("PermissionsComponent")
    .initialState(State(Seq.empty, "action", SortOrder.Desc))
    .renderBackend[Backend]
    .componentWillMount { $ =>
      $.backend.getPermissions($.props.repositoryName, $.state.sortColumn, $.state.sortOrder)
    }
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String) =
    component.apply(Props(ctl, repositoryName))
}
