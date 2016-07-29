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
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.Role
import io.fcomb.rpc.OrganizationGroupResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object GroupComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], orgName: String, name: String)
  final case class FormState(name: String, role: Role, isFormDisabled: Boolean)
  final case class State(group: Option[OrganizationGroupResponse], form: Option[FormState])

  class Backend($ : BackendScope[Props, State]) {
    def getGroup(name: String): Callback = {
      Callback.future {
        Rpc.call[OrganizationGroupResponse](RpcMethod.GET, Resource.group(name)).map {
          case Xor.Right(group) =>
            $.modState(_.copy(group = Some(group)))
          case Xor.Left(e) =>
            println(e)
            Callback.empty
        }
      }
    }

    def renderGroup(groupOpt: Option[OrganizationGroupResponse]) = {
      groupOpt match {
        case Some(group) =>
          <.div(
            <.h2(s"Group ${group.name}"),
            <.label("Role: ", <.span(group.role.toString())),
            <.h3("Members")
          )
        case None => EmptyTag
      }
    }

    def render(props: Props, state: State) = {
      <.section(
        renderGroup(state.group)
      )
    }
  }

  private val component = ReactComponentB[Props]("GroupComponent")
    .initialState(State(None, None))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getGroup($.props.name))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], orgName: String, name: String) =
    component(Props(ctl, orgName, name))
}
