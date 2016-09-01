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
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object RepositoryVisibilityComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         repositoryName: String,
                         kind: ImageVisibilityKind)
  final case class State(kind: Option[ImageVisibilityKind], isFormDisabled: Boolean)

  class Backend($ : BackendScope[Props, State]) {
    def updateVisibilityKind(e: ReactEventI, value: String): Callback = {
      val kind = ImageVisibilityKind.withName(value)
      (for {
        props <- $.props
        state <- $.state
      } yield (props, state)).flatMap {
        case (props, state) if !state.isFormDisabled && !state.kind.contains(kind) =>
          $.setState(state.copy(isFormDisabled = true)) >>
            Callback.future {
              Rpc
                .call[Unit](RpcMethod.POST,
                            Resource.repositoryVisibility(props.repositoryName, kind))
                .map {
                  case Xor.Right(_) =>
                    $.modState(_.copy(kind = Some(kind), isFormDisabled = false))
                  case Xor.Left(e) =>
                    // TODO
                    updateFormDisabled(false)
                }
                .recover {
                  case _ => updateFormDisabled(false)
                }
            }
        case _ => Callback.empty
      }
    }

    def handleOnSubmit(e: ReactEventH): Callback =
      e.preventDefaultCB

    def updateFormDisabled(isFormDisabled: Boolean): Callback =
      $.modState(_.copy(isFormDisabled = isFormDisabled))

    def render(props: Props, state: State) = {
      val kind = state.kind.getOrElse(props.kind)
      <.div(<.h2("Repository visibility"),
            <.form(^.onSubmit ==> handleOnSubmit,
                   ^.disabled := state.isFormDisabled,
                   MuiRadioButtonGroup(name = "visibilityKind",
                                       defaultSelected = kind.value,
                                       onChange = updateVisibilityKind _)(
                     MuiRadioButton(
                       key = "public",
                       value = ImageVisibilityKind.Public.value,
                       label = "Public",
                       disabled = state.isFormDisabled
                     )(),
                     MuiRadioButton(
                       key = "private",
                       value = ImageVisibilityKind.Private.value,
                       label = "Private",
                       disabled = state.isFormDisabled
                     )())))
    }
  }

  private val component = ReactComponentB[Props]("RepositoryVisibility")
    .initialState(State(None, false))
    .renderBackend[Backend]
    .componentWillMount($ => $.modState(_.copy(kind = Some($.props.kind))))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String, kind: ImageVisibilityKind) =
    component.apply(Props(ctl, repositoryName, kind))
}
