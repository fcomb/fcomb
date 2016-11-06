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
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.Implicits._
import io.fcomb.frontend.components.LayoutComponent
import io.fcomb.frontend.styles.App
import io.fcomb.models.Owner
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object NewRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], namespace: OwnerNamespace)
  final case class State(owner: Option[Owner],
                         name: String,
                         visibilityKind: ImageVisibilityKind,
                         description: String,
                         errors: Map[String, String],
                         isDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    import RepositoryForm._

    def create(props: Props): Callback =
      $.state.flatMap { state =>
        state.owner match {
          case Some(owner) if !state.isDisabled =>
            $.setState(state.copy(isDisabled = true)).flatMap { _ =>
              Callback.future {
                Rpc
                  .createRepository(owner, state.name, state.visibilityKind, state.description)
                  .map {
                    case Xor.Right(repository) =>
                      props.ctl.set(DashboardRoute.Repository(repository.slug))
                    case Xor.Left(errs) =>
                      $.setState(state.copy(isDisabled = false, errors = foldErrors(errs)))
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
                                 canCreateRoleOnly = true,
                                 isAllNamespace = false,
                                 isDisabled = state.isDisabled,
                                 isFullWidth = true,
                                 cb = updateNamespace _)),
        <.div(
          LayoutComponent.helpBlockClass,
          ^.style := App.helpBlockStyle,
          <.label(^.`for` := "namespace", "An account which will be the owner of repository.")))

    def renderName(state: State) =
      <.div(^.`class` := "row",
            ^.key := "nameRow",
            <.div(^.`class` := "col-xs-6",
                  MuiTextField(floatingLabelText = "Name",
                               id = "name",
                               name = "name",
                               disabled = state.isDisabled,
                               errorText = state.errors.get("name"),
                               fullWidth = true,
                               value = state.name,
                               onChange = updateName _)()),
            <.div(LayoutComponent.helpBlockClass,
                  ^.style := App.helpBlockStyle,
                  <.label(^.`for` := "name",
                          "Enter a name for repository that will be used by docker or rkt.")))

    def cancel(e: ReactEventH): Callback =
      e.preventDefaultCB >> $.props.flatMap(_.ctl.set(DashboardRoute.Repositories))

    def renderFormButtons(state: State) = {
      val createIsDisabled = state.isDisabled || state.owner.isEmpty
      <.div(^.`class` := "row",
            ^.style := App.paddingTopStyle,
            ^.key := "actionsRow",
            <.div(^.`class` := "col-xs-12",
                  MuiRaisedButton(`type` = "button",
                                  primary = false,
                                  label = "Cancel",
                                  style = App.cancelStyle,
                                  disabled = state.isDisabled,
                                  onTouchTap = cancel _,
                                  key = "cancel")(),
                  MuiRaisedButton(`type` = "submit",
                                  primary = true,
                                  label = "Create",
                                  disabled = createIsDisabled,
                                  key = "submit")()))
    }

    def renderForm(props: Props, state: State) =
      <.form(
        ^.onSubmit ==> handleOnSubmit(props),
        ^.disabled := state.isDisabled,
        ^.key := "form",
        MuiCardText(key = "form")(
          renderNamespace(props, state),
          renderName(state),
          renderDescription(state.description, state.errors, state.isDisabled, updateDescription),
          renderVisiblity(state.visibilityKind, state.isDisabled, updateVisibilityKind),
          renderFormButtons(state)))

    def render(props: Props, state: State) =
      MuiCard()(<.div(^.key := "header",
                      App.formTitleBlock,
                      MuiCardTitle(key = "title")(<.h1(App.cardTitle, "New repository"))),
                renderForm(props, state))
  }

  private val component =
    ReactComponentB[Props]("NewRepository")
      .initialState(State(None, "", ImageVisibilityKind.Private, "", Map.empty, false))
      .renderBackend[Backend]
      .build

  def apply(ctl: RouterCtl[DashboardRoute], namespace: OwnerNamespace) =
    component(Props(ctl, namespace))
}
