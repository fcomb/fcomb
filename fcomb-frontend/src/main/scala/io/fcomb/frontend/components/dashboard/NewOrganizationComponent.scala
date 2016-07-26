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
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.rpc.Formats._
import io.fcomb.rpc.{OrganizationCreateRequest, OrganizationResponse}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object NewOrganizationComponent {
  final case class State(name: String, isFormDisabled: Boolean)

  final case class Backend($ : BackendScope[RouterCtl[DashboardRoute], State]) {
    def create(ctl: RouterCtl[DashboardRoute]): Callback = {
      $.state.flatMap { state =>
        if (state.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
            Callback.future {
              val req = OrganizationCreateRequest(state.name)
              Rpc
                .callWith[OrganizationCreateRequest, OrganizationResponse](RpcMethod.POST,
                                                                           Resource.organizations,
                                                                           req)
                .map {
                  case Xor.Right(org) => ctl.set(DashboardRoute.Organization(org.name))
                  case Xor.Left(e)    => $.setState(state.copy(isFormDisabled = false))
                }
                .recover {
                  case _ => $.setState(state.copy(isFormDisabled = false))
                }
            }
          }
        }
      }
    }

    def handleOnSubmit(ctl: RouterCtl[DashboardRoute])(e: ReactEventH): Callback = {
      e.preventDefaultCB >> create(ctl)
    }

    def updateName(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(name = value))
    }

    def render(ctl: RouterCtl[DashboardRoute], state: State) = {
      <.div(<.h2("New organization"),
            <.form(
              ^.onSubmit ==> handleOnSubmit(ctl),
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
              <.input.submit(^.tabIndex := 2, ^.value := "Create")
            ))
    }
  }

  private val component = ReactComponentB[RouterCtl[DashboardRoute]]("NewOrganizationComponent")
    .initialState(State("", false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) = component(ctl)
}
