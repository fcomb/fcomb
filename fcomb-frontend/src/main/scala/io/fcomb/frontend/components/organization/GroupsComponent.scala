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
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.PaginationData
import io.fcomb.rpc.OrganizationGroupResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object GroupsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], orgName: String)
  final case class FormState(name: String, isFormDisabled: Boolean)
  final case class State(groups: Seq[OrganizationGroupResponse], form: Option[FormState])

  final case class Backend($ : BackendScope[Props, State]) {
    def getGroups(orgName: String): Callback = {
      Callback.future {
        Rpc
          .call[PaginationData[OrganizationGroupResponse]](RpcMethod.GET,
                                                           Resource.organizationGroups(orgName))
          .map {
            case Xor.Right(pd) =>
              $.modState(_.copy(groups = pd.data))
            case Xor.Left(e) =>
              println(e)
              Callback.empty
          }
      }
    }

    def renderGroup(ctl: RouterCtl[DashboardRoute], group: OrganizationGroupResponse) = {
      <.li(group.name)
    }

    def render(props: Props, state: State) = {
      <.div(
        <.h2("Groups"),
        <.ul(state.groups.map(renderGroup(props.ctl, _)))
      )
    }
  }

  private val component = ReactComponentB[Props]("GroupsComponent")
    .initialState(State(Seq.empty, None))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getGroups($.props.orgName))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], orgName: String) =
    component(Props(ctl, orgName))
}
