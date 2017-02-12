/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Form
import io.fcomb.frontend.components.Helpers._
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
                         isDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    import RepositoryForm._

    def create(props: Props): Callback =
      $.state.flatMap { state =>
        state.owner match {
          case Some(owner) if !state.isDisabled =>
            $.modState(_.copy(isDisabled = true)).flatMap { _ =>
              Callback.future {
                Rpc
                  .createRepository(owner, state.name, state.visibilityKind, state.description)
                  .map {
                    case Right(repository) =>
                      props.ctl.set(DashboardRoute.Repository(repository.slug))
                    case Left(errs) =>
                      $.modState(_.copy(isDisabled = false, errors = foldErrors(errs)))
                  }
              }
            }
          case _ => Callback.empty
        }
      }

    def handleOnSubmit(props: Props)(e: ReactEventH): Callback =
      e.preventDefaultCB >> create(props)

    def updateName(name: String): Callback =
      $.modState(_.copy(name = name))

    def updateVisibilityKind(e: ReactEventI, value: String): Callback = {
      val kind = ImageVisibilityKind.withName(value)
      $.modState(_.copy(visibilityKind = kind))
    }

    def updateDescription(description: String): Callback =
      $.modState(_.copy(description = description))

    def updateNamespace(namespace: Namespace) =
      namespace match {
        case on: OwnerNamespace => $.modState(_.copy(owner = on.toOwner))
        case _                  => Callback.empty
      }

    def renderNamespace(props: Props, state: State) =
      Form.row(
        NamespaceComponent(props.namespace,
                           canCreateRoleOnly = true,
                           isAllNamespace = false,
                           isDisabled = state.isDisabled,
                           isFullWidth = true,
                           cb = updateNamespace _),
        <.label(^.`for` := "namespace", "An account which will be the owner of repository."),
        "namespace"
      )

    def renderName(state: State) =
      Form.row(
        Form.textField(label = "Name",
                       key = "name",
                       isDisabled = state.isDisabled,
                       errors = state.errors,
                       value = state.name,
                       onChange = updateName _),
        <.label(^.`for` := "name",
                "Enter a name for repository that will be used by docker or rkt."),
        "name"
      )

    def cancel(e: ReactEventH): Callback =
      e.preventDefaultCB >> $.props.flatMap(_.ctl.set(DashboardRoute.Repositories))

    def renderFormButtons(state: State) =
      <.div(
        ^.`class` := "row",
        ^.style := App.paddingTopStyle,
        ^.key := "actionsRow",
        <.div(
          ^.`class` := "col-xs-12",
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
                          disabled = state.isDisabled,
                          key = "submit")()
        )
      )

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
          renderFormButtons(state)
        )
      )

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
