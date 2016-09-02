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
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.{Owner, OwnerKind}
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.rpc.docker.distribution.{RepositoryResponse, ImageCreateRequest}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSConverters._

object NewRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], ownerScope: RepositoryOwnerScope)
  final case class State(owner: Option[Owner],
                         name: String,
                         visibilityKind: ImageVisibilityKind,
                         description: Option[String],
                         errors: Map[String, String],
                         isFormDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    def create(props: Props): Callback = {
      $.state.flatMap { state =>
        state.owner match {
          case Some(owner) if !state.isFormDisabled =>
            $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
              val url = owner match {
                case Owner(_, OwnerKind.User)          => Resource.userSelfRepositories
                case Owner(id, OwnerKind.Organization) => Resource.organizationRepositories(id.toString)
              }
              Callback.future {
                val req = ImageCreateRequest(state.name, state.visibilityKind, state.description)
                Rpc
                  .callWith[ImageCreateRequest, RepositoryResponse](RpcMethod.POST, url, req)
                  .map {
                  case Xor.Right(repository) =>
                    props.ctl.set(DashboardRoute.Repository(repository.slug))
                  case Xor.Left(errs) =>
                    $.setState(state.copy(isFormDisabled = false, errors = foldErrors(errs)))
                }
                  .recover {
                  case _ => $.setState(state.copy(isFormDisabled = false))
                }
              }
            }
          case _ =>Callback.empty
        }
      }
    }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback =
      e.preventDefaultCB >> create(props)

    def updateName(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(name = value))
    }

    def updateVisibilityKind(e: ReactEventI, value: String): Callback = {
      val kind = ImageVisibilityKind.withName(value)
      $.modState(_.copy(visibilityKind = kind))
    }

    def updateDescription(e: ReactEventI): Callback = {
      val value = e.target.value match {
        case s if s.nonEmpty => Some(s)
        case _               => None
      }
      $.modState(_.copy(description = value))
    }

    def updateOwnerItem(ownerItem: OwnerItem) =
      $.modState(_.copy(owner = Some(ownerItem.toOwner)))

    def render(props: Props, state: State) = {
      <.div(
        <.h2("New repository"),
        <.form(
          ^.onSubmit ==> handleOnSubmit(props),
          ^.disabled := state.isFormDisabled,
          <.div(^.display.flex,
                ^.flexDirection.column,
                OwnerComponent(props.ownerScope, true, state.isFormDisabled, updateOwnerItem _),
                MuiTextField(floatingLabelText = "Name",
                             id = "name",
                             name = "name",
                             disabled = state.isFormDisabled,
                             errorText = state.errors.get("name"),
                             value = state.name,
                             onChange = updateName _)(),
                MuiTextField(floatingLabelText = "Description",
                             id = "description",
                             name = "description",
                             multiLine = true,
                             disabled = state.isFormDisabled,
                             errorText = state.errors.get("description"),
                             value = state.description.orUndefined,
                             onChange = updateDescription _)(),
                <.label(^.`for` := "visibilityKind", "Visibility"),
                MuiRadioButtonGroup(name = "visibilityKind",
                                    defaultSelected = state.visibilityKind.value,
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
                  )()
                ),
                MuiRaisedButton(`type` = "submit",
                                primary = true,
                                label = "Create",
                                disabled = state.isFormDisabled || state.owner.isEmpty)()))
      )
    }
  }

  private val component = ReactComponentB[Props]("NewRepository")
    .initialState(State(None, "", ImageVisibilityKind.Private, None, Map.empty, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], ownerScope: RepositoryOwnerScope) =
    component(Props(ctl, ownerScope))
}
