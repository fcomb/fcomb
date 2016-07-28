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
import io.fcomb.rpc.{OrganizationGroupCreateRequest, OrganizationGroupResponse}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object NewGroupComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], orgName: String)
  final case class State(name: String, role: Role, isFormDisabled: Boolean)

  class Backend($ : BackendScope[Props, State]) {
    def create(props: Props): Callback = {
      $.state.flatMap { state =>
        if (state.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
            Callback.future {
              val req = OrganizationGroupCreateRequest(state.name, state.role)
              Rpc
                .callWith[OrganizationGroupCreateRequest, OrganizationGroupResponse](
                  RpcMethod.POST,
                  Resource.organizationGroups(props.orgName),
                  req)
                .map {
                  case Xor.Right(group) =>
                    props.ctl.set(DashboardRoute.OrganizationGroup(props.orgName, group.name))
                  case Xor.Left(e) => $.setState(state.copy(isFormDisabled = false))
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

    def updateRole(e: ReactEventI): Callback = {
      val value = Role.withName(e.target.value)
      $.modState(_.copy(role = value))
    }

    def render(props: Props, state: State) = {
      <.div(
        <.h2("New group"),
        <.form(^.onSubmit ==> handleOnSubmit(props),
               ^.disabled := state.isFormDisabled,
               <.label(^.`for` := "name", "Name"),
               <.input.text(^.id := "name",
                            ^.name := "name",
                            ^.autoFocus := true,
                            ^.required := true,
                            ^.tabIndex := 1,
                            ^.value := state.name,
                            ^.onChange ==> updateName),
               <.br,
               <.label(^.`for` := "role", "Role"),
               <.select(^.id := "role",
                        ^.name := "role",
                        ^.required := true,
                        ^.tabIndex := 2,
                        ^.value := state.role.value,
                        ^.onChange ==> updateRole,
                        Role.values.map(r => <.option(^.value := r.value)(r.entryName))),
               <.br,
               <.br,
               <.input.submit(^.tabIndex := 3, ^.value := "Create"))
      )
    }
  }

  private val component = ReactComponentB[Props]("NewGroupComponent")
    .initialState(State("", Role.Member, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], orgName: String) =
    component(Props(ctl, orgName))
}
