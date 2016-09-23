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
import io.fcomb.frontend.styles.Global
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.models.{Owner, OwnerKind}
import io.fcomb.rpc.docker.distribution.{RepositoryResponse, ImageCreateRequest}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js.JSConverters._
import scalacss.ScalaCssReact._

object NewRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], namespace: OwnerNamespace)
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
                case Owner(_, OwnerKind.User) => Resource.userSelfRepositories
                case Owner(id, OwnerKind.Organization) =>
                  Resource.organizationRepositories(id.toString)
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
          case _ => Callback.empty
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

    def updateNamespace(namespace: Namespace) = {
      namespace match {
        case on: OwnerNamespace => $.modState(_.copy(owner = on.toOwner))
        case _                  => Callback.empty
      }
    }

    def render(props: Props, state: State) = {
      MuiCard()(
        <.div(Global.formTitleBlock,
          MuiCardTitle(key = "title")(<.h1(Global.formTitle, "New repository"))),
        <.form(^.onSubmit ==> handleOnSubmit(props),
               ^.disabled := state.isFormDisabled,
          MuiCardText(key = "form")(
            <.div(^.`class` := "container-fluid",
              <.div(^.`class` := "row",
                <.div(^.`class` := "col-xs-6",
                  NamespaceComponent(props.namespace,
                                     isAdminRoleOnly = true,
                                     isAllNamespace = false,
                                     isDisabled = state.isFormDisabled,
                                     cb = updateNamespace _,
                    fullWidth = true)),
                <.div(^.`class` := s"col-xs-6 ${Global.helpBlock.htmlClass}",
                  "An account which will be the owner of repository.")),
              <.div(^.`class` := "row",
                                <.div(^.`class` := "col-xs-6",
                 MuiTextField(floatingLabelText = "Name",
                              id = "name",
                              name = "name",
                              disabled = state.isFormDisabled,
                   errorText = state.errors.get("name"),
                   fullWidth = true,
                              value = state.name,
                   onChange = updateName _)()),
                <.div(^.`class` := s"col-xs-6 ${Global.helpBlock.htmlClass}",
                  "Enter a name for repository that will be used by docker or rkt.")),
              <.div(^.`class` := "row",
                                <.div(^.`class` := "col-xs-6",
                 MuiTextField(floatingLabelText = "Description (Markdown)",
                              id = "description",
                              name = "description",
                              multiLine = true,
                              fullWidth = true,
                              rows = 3,
                              rowsMax = 15,
                              disabled = state.isFormDisabled,
                              errorText = state.errors.get("description"),
                              value = state.description.orUndefined,
                   onChange = updateDescription _)())),
              <.div(^.`class` := "row",
                                <.div(^.`class` := "col-xs-6",
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
                 ))))),
          // TODO: "create another one" checkbox
               MuiCardActions(key = "actions")(
                 MuiRaisedButton(`type` = "submit",
                                 primary = true,
                                 label = "Create",
                                 disabled = state.isFormDisabled || state.owner.isEmpty,
                                 key = "submit")()))
      )
    }
  }

  private val component = ReactComponentB[Props]("NewRepository")
    .initialState(State(None, "", ImageVisibilityKind.Private, None, Map.empty, false))
    .renderBackend[Backend]
    .build

  def apply(ctl: RouterCtl[DashboardRoute], namespace: OwnerNamespace) =
    component(Props(ctl, namespace))
}
