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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import diode.data.{Empty, Failed, Pot, Ready}
import diode.react.ReactPot._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.organization.GroupForm._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import io.fcomb.models.acl.Role
import io.fcomb.models.errors.ErrorsException
import io.fcomb.rpc.OrganizationGroupResponse
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object EditGroupComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String, group: String)
  final case class State(group: Pot[OrganizationGroupResponse],
                         form: Option[FormState],
                         isDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    def modFormState(f: FormState => FormState): Callback =
      $.modState(st => st.copy(form = st.form.map(f)))

    def tryAcquireState(f: State => Callback) =
      $.state.flatMap { state =>
        if (state.isDisabled) Callback.warn("State is already acquired")
        else
          $.modState(_.copy(isDisabled = true)) >> f(state).finallyRun(
            $.modState(_.copy(isDisabled = false)))
      }

    def get(): Callback =
      $.props.flatMap { props =>
        Callback.future(Rpc.getOrgaizationGroup(props.slug, props.group).map {
          case Right(group) =>
            val form = FormState(group.name, group.role, Map.empty)
            $.modState(st => st.copy(group = Ready(group), form = Some(form)))
          case Left(errs) => $.modState(_.copy(group = Failed(ErrorsException(errs))))
        })
      }

    def update(): Callback =
      $.props.flatMap { props =>
        tryAcquireState { state =>
          (state.group, state.form) match {
            case (Ready(group), Some(form)) =>
              Callback.future(
                Rpc.updateOrganizationGroup(props.slug, group.name, form.name, form.role).map {
                  case Right(_) =>
                    props.ctl.set(DashboardRoute.OrganizationGroup(props.slug, form.name))
                  case Left(errs) => modFormState(_.copy(errors = foldErrors(errs)))
                })
            case _ => Callback.empty
          }
        }
      }

    def handleOnSubmit(e: ReactEventH): Callback =
      e.preventDefaultCB >> update()

    def updateName(name: String): Callback =
      modFormState(_.copy(name = name))

    def updateRole(role: Role): Callback =
      modFormState(_.copy(role = role))

    def cancel(e: ReactEventH): Callback =
      e.preventDefaultCB >> $.props.flatMap(p =>
        p.ctl.set(DashboardRoute.OrganizationGroups(p.slug)))

    def renderFormButtons(state: State) =
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
                                  label = "Update",
                                  disabled = state.isDisabled,
                                  key = "update")()))

    def renderForm(props: Props, state: State): ReactNode =
      state.form match {
        case Some(form) =>
          <.form(^.onSubmit ==> handleOnSubmit,
                 ^.disabled := state.isDisabled,
                 ^.key := "form",
                 MuiCardText(key = "form")(renderFormName(form, state.isDisabled, updateName _),
                                           renderFormRole(form, state.isDisabled, updateRole _),
                                           renderFormButtons(state)))
        case _ => <.div()
      }

    def render(props: Props, state: State) =
      MuiCard()(<.div(^.key := "header",
                      App.formTitleBlock,
                      MuiCardTitle(key = "title")(<.h1(App.cardTitle, "Edit group"))),
                <.section(state.group.render(_ => renderForm(props, state))))
  }

  private val component = ReactComponentB[Props]("EditGroup")
    .initialState(State(Empty, None, isDisabled = false))
    .renderBackend[Backend]
    .componentDidMount(_.backend.get())
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String, group: String) =
    component(Props(ctl, slug, group))
}
