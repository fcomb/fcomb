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
import io.fcomb.rpc.docker.distribution.{RepositoryResponse, ImageCreateRequest}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object NewRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], ownerScope: OwnerScope)
  final case class State(name: String,
                         visibilityKind: ImageVisibilityKind,
                         description: Option[String],
                         isFormDisabled: Boolean)

  class Backend($ : BackendScope[Props, State]) {
    def create(props: Props): Callback = {
      $.state.flatMap { state =>
        if (state.isFormDisabled) Callback.empty
        else {
          $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
            Callback.future {
              val req = ImageCreateRequest(state.name, state.visibilityKind, state.description)
              Rpc
                .callWith[ImageCreateRequest, RepositoryResponse](RpcMethod.POST,
                                                             Resource.userSelfRepositories,
                                                             req)
                .map {
                  case Xor.Right(repository) => props.ctl.set(DashboardRoute.Repository(repository.slug))
                  case Xor.Left(e)  => $.setState(state.copy(isFormDisabled = false))
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

    def updateVisibilityKind(e: ReactEventI): Callback = {
      val value = ImageVisibilityKind.withName(e.target.value)
      $.modState(_.copy(visibilityKind = value))
    }

    def updateDescription(e: ReactEventI): Callback = {
      val value = e.target.value match {
        case s if s.nonEmpty => Some(s)
        case _               => None
      }
      $.modState(_.copy(description = value))
    }

    def render(props: Props, state: State) = {
      <.div(
        <.h2("New repository"),
        <.form(
          ^.onSubmit ==> handleOnSubmit(props),
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
          <.label(^.`for` := "visibilityKind", "Visibility"),
          <.select(^.id := "visibilityKind",
                   ^.name := "visibilityKind",
                   ^.required := true,
                   ^.tabIndex := 2,
                   ^.value := state.visibilityKind.value,
                   ^.onChange ==> updateVisibilityKind,
                   ImageVisibilityKind.values.map(k => <.option(^.value := k.value)(k.entryName))),
          <.br,
          <.label(^.`for` := "description", "Description"),
          <.textarea(^.id := "description",
                     ^.name := "description",
                     ^.tabIndex := 3,
                     ^.value := state.description.getOrElse(""),
                     ^.onChange ==> updateDescription),
          <.br,
          <.input.submit(^.tabIndex := 4, ^.value := "Create"))
      )
    }
  }

  private val component = ReactComponentB[Props]("NewRepository")
    .initialState(State("", ImageVisibilityKind.Private, None, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], ownerScope: OwnerScope) =
    component(Props(ctl, ownerScope))
}
