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
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object DeleteRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], repositoryName: String)
  final case class State(isDisabled: Boolean)

  class Backend($ : BackendScope[Props, State]) {
    def delete(props: Props)(e: ReactEventI): Callback = {
      e.preventDefaultCB >>
        $.state.flatMap { state => // TODO: DRY
          $.setState(state.copy(isDisabled = true)) >>
            Callback.future {
              Rpc
                .call[Unit](RpcMethod.DELETE, Resource.repository(props.repositoryName))
                .map {
                  case Xor.Right(_) =>
                    updateDisabled(false) >>
                      props.ctl.set(DashboardRoute.Root)
                  case Xor.Left(e) =>
                    // TODO
                    updateDisabled(false)
                }
                .recover {
                  case _ => updateDisabled(false)
                }
            }
        }
    }

    def updateDisabled(isDisabled: Boolean): Callback = {
      $.modState(_.copy(isDisabled = isDisabled))
    }

    def render(props: Props, state: State) = {
      <.div(<.h2("Delete repository"),
            <.button(^.`type` := "button",
                     ^.disabled := state.isDisabled,
                     ^.onClick ==> delete(props),
                     "Destroy"))
    }
  }

  private val component = ReactComponentB[Props]("DeleteRepository")
    .initialState(State(false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String) =
    component.apply(Props(ctl, repositoryName))
}
