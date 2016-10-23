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
import io.fcomb.rpc.{OrganizationCreateRequest, OrganizationResponse}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object NewOrganizationComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])
  final case class State(name: String, errors: Map[String, String], isFormDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
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

    def handleOnSubmit(ctl: RouterCtl[DashboardRoute])(e: ReactEventH): Callback = {
      e.preventDefaultCB >> create(ctl)
    }

    def updateName(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(name = value))
    }

    def render(props: Props, state: State) = {
      <.div(<.h2("New organization"),
            <.form(
              ^.onSubmit ==> handleOnSubmit(props.ctl),
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
                                 MuiRaisedButton(`type` = "submit",
                                                 primary = true,
                                                 label = "Create",
                                                 disabled = state.isFormDisabled)())))
    }
  }

  private val component = ReactComponentB[Props]("NewOrganization")
    .initialState(State("", Map.empty, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component(Props(ctl))
}
