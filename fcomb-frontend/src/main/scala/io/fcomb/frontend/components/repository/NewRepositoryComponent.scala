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
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.models.Owner
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object NewRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], namespace: OwnerNamespace)
  final case class State(owner: Option[Owner],
                         name: String,
                         visibilityKind: ImageVisibilityKind,
                         description: String,
                         errors: Map[String, String],
                         isFormDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    import RepositoryForm._

    def create(props: Props): Callback =
      $.state.flatMap { state =>
        state.owner match {
          case Some(owner) if !state.isFormDisabled =>
            $.setState(state.copy(isFormDisabled = true)).flatMap { _ =>
              Callback.future {
                Rpc
                  .createRepository(owner, state.name, state.visibilityKind, state.description)
                  .map {
                    case Xor.Right(repository) =>
                      props.ctl.set(DashboardRoute.Repository(repository.slug))
                    case Xor.Left(errs) =>
                      $.setState(state.copy(isFormDisabled = false, errors = foldErrors(errs)))
                  }
              }
            }
          case _ => Callback.empty
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
      val value = e.target.value
      $.modState(_.copy(description = value))
    }

    def updateNamespace(namespace: Namespace) =
      namespace match {
        case on: OwnerNamespace => $.modState(_.copy(owner = on.toOwner))
        case _                  => Callback.empty
      }

    def renderNamespace(props: Props, state: State) =
      <.div(
        ^.`class` := "row",
        ^.key := "namespaceRow",
        <.div(^.`class` := "col-xs-6",
              NamespaceComponent(props.namespace,
                                 isAdminRoleOnly = true,
                                 isAllNamespace = false,
                                 isDisabled = state.isFormDisabled,
                                 cb = updateNamespace _,
                                 fullWidth = true)),
        <.div(
          helpBlockClass,
          ^.style := helpBlockPadding,
          <.label(^.`for` := "namespace", "An account which will be the owner of repository.")))

    def renderName(state: State) =
      <.div(^.`class` := "row",
            ^.key := "nameRow",
            <.div(^.`class` := "col-xs-6",
                  MuiTextField(floatingLabelText = "Name",
                               id = "name",
                               name = "name",
                               disabled = state.isFormDisabled,
                               errorText = state.errors.get("name"),
                               fullWidth = true,
                               value = state.name,
                               onChange = updateName _)()),
            <.div(helpBlockClass,
                  ^.style := helpBlockPadding,
                  <.label(^.`for` := "name",
                          "Enter a name for repository that will be used by docker or rkt.")))

    def renderActions(state: State) = {
      val createIsDisabled = state.isFormDisabled || state.owner.isEmpty
      <.div(^.`class` := "row",
            ^.style := paddingTop,
            ^.key := "actionsRow",
            <.div(^.`class` := "col-xs-12",
                  MuiRaisedButton(`type` = "submit",
                                  primary = true,
                                  label = "Create",
                                  disabled = createIsDisabled,
                                  key = "submit")()))
    }

    def render(props: Props, state: State) =
      MuiCard()(<.div(^.key := "header",
                      App.formTitleBlock,
                      MuiCardTitle(key = "title")(<.h1(App.formTitle, "New repository"))),
                <.form(^.onSubmit ==> handleOnSubmit(props),
                       ^.disabled := state.isFormDisabled,
                       ^.key := "form",
                       MuiCardText(key = "form")(renderNamespace(props, state),
                                                 renderName(state),
                                                 renderDescription(state.description,
                                                                   state.errors,
                                                                   state.isFormDisabled,
                                                                   updateDescription),
                                                 renderVisiblity(state.visibilityKind,
                                                                 state.isFormDisabled,
                                                                 updateVisibilityKind),
                                                 renderActions(state))))
  }

  private val component =
    ReactComponentB[Props]("NewRepository")
      .initialState(State(None, "", ImageVisibilityKind.Private, "", Map.empty, false))
      .renderBackend[Backend]
      .build

  def apply(ctl: RouterCtl[DashboardRoute], namespace: OwnerNamespace) =
    component(Props(ctl, namespace))
}
