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
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object RepositoryVisibilityComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], repositoryName: String)
  final case class State(kind: Option[ImageVisibilityKind], isFormDisabled: Boolean)

  class Backend($ : BackendScope[Props, State]) {
    // TODO: DRY with Diode Pot
    def getVisibility(name: String): Callback = {
      Callback.future {
        Rpc.call[RepositoryResponse](RpcMethod.GET, Resource.repository(name)).map {
          case Xor.Right(repository) =>
            $.modState(_.copy(kind = Some(repository.visibilityKind)))
          case Xor.Left(e) =>
            println(e)
            Callback.empty
        }
      }
    }

    def updateVisibility(props: Props, kind: ImageVisibilityKind)(e: ReactEventI): Callback = {
      e.preventDefaultCB >>
        $.state.flatMap { state => // TODO: DRY
          $.setState(state.copy(isFormDisabled = true)) >>
            Callback.future {
              Rpc
                .call[Unit](RpcMethod.POST,
                            Resource.repositoryVisibility(props.repositoryName, kind))
                .map {
                  case Xor.Right(_) =>
                    updateFormDisabled(false) >> getVisibility(props.repositoryName)
                  case Xor.Left(e) =>
                    // TODO
                    updateFormDisabled(false)
                }
                .recover {
                  case _ => updateFormDisabled(false)
                }
            }
        }
    }

    def handleOnSubmit(e: ReactEventH): Callback = {
      e.preventDefaultCB
    }

    def updateFormDisabled(isFormDisabled: Boolean): Callback = {
      $.modState(_.copy(isFormDisabled = isFormDisabled))
    }

    def render(props: Props, state: State) = {
      <.div(
        <.h2("Repository visibility"),
        <.form(^.onSubmit ==> handleOnSubmit,
               ^.disabled := state.isFormDisabled,
               <.input.radio(^.id := "public",
                             ^.name := "public",
                             ^.tabIndex := 1,
                             ^.checked := state.kind.contains(ImageVisibilityKind.Public),
                             ^.disabled := state.kind.contains(ImageVisibilityKind.Public),
                             ^.onClick ==> updateVisibility(props, ImageVisibilityKind.Public)),
               <.label(^.`for` := "public", "Public"),
               <.br,
               <.input.radio(^.id := "private",
                             ^.name := "private",
                             ^.tabIndex := 2,
                             ^.checked := state.kind.contains(ImageVisibilityKind.Private),
                             ^.disabled := state.kind.contains(ImageVisibilityKind.Private),
                             ^.onClick ==> updateVisibility(props, ImageVisibilityKind.Private)),
               <.label(^.`for` := "private", "Private")))
    }
  }

  private val component = ReactComponentB[Props]("RepositoryVisibility")
    .initialState(State(None, false))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getVisibility($.props.repositoryName))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String) =
    component.apply(Props(ctl, repositoryName))
}
