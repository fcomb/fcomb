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
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.Role
import io.fcomb.rpc.{OrganizationGroupRequest, OrganizationGroupResponse}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object NewGroupComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], orgName: String)
  final case class State(name: String,
                         role: Role,
                         errors: Map[String, String],
                         isFormDisabled: Boolean)

  class Backend($ : BackendScope[Props, State]) {
    def create(props: Props): Callback = {
      $.state.flatMap { state =>
        if (state.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
            Callback.future {
              val req = OrganizationGroupRequest(state.name, state.role)
              Rpc
                .callWith[OrganizationGroupRequest, OrganizationGroupResponse](
                  RpcMethod.POST,
                  Resource.organizationGroups(props.orgName),
                  req)
                .map {
                  case Xor.Right(group) =>
                    props.ctl.set(DashboardRoute.OrganizationGroup(props.orgName, group.name))
                  case Xor.Left(errs) =>
                    $.setState(state.copy(isFormDisabled = false, errors = foldErrors(errs)))
                }
                .recover {
                  case _ => $.setState(state.copy(isFormDisabled = false))
                }
            }
          }
        }
      }
    }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback = {
      e.preventDefaultCB >> create(props)
    }

    def updateName(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(name = value))
    }

    def updateRole(e: ReactEventI, idx: Int, role: Role): Callback =
      $.modState(_.copy(role = role))

    lazy val roles = Role.values.map(r =>
      MuiMenuItem[Role](key = r.value, value = r, primaryText = r.entryName)())

    def render(props: Props, state: State) = {
      <.div(
        <.h2("New group"),
        <.form(^.onSubmit ==> handleOnSubmit(props),
               ^.disabled := state.isFormDisabled,
               <.div(^.display.flex,
                     ^.flexDirection.column,
                     MuiTextField(floatingLabelText = "Name",
                                  id = "name",
                                  name = "name",
                                  disabled = state.isFormDisabled,
                                  errorText = state.errors.get("name"),
                                  value = state.name,
                                  onChange = updateName _)(),
                     MuiSelectField[Role](id = "role",
                                          floatingLabelText = "Role",
                                          errorText = state.errors.get("role"),
                                          value = state.role,
                                          onChange = updateRole _)(roles),
                     MuiRaisedButton(`type` = "submit",
                                     primary = true,
                                     label = "Create",
                                     disabled = state.isFormDisabled)()))
      )
    }
  }

  private val component = ReactComponentB[Props]("NewGroup")
    .initialState(State("", Role.Member, Map.empty, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], orgName: String) =
    component(Props(ctl, orgName))
}
