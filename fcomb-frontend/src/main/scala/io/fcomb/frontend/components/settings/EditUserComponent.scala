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

package io.fcomb.frontend.components.settings

import chandu0101.scalajs.react.components.materialui._
import diode.data.{Empty, Failed, Pot, Ready}
import diode.react.ReactPot._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Helpers._
import io.fcomb.frontend.components.settings.UserForm._
import io.fcomb.frontend.styles.App
import io.fcomb.frontend.utils.StringUtils
import io.fcomb.models.UserRole
import io.fcomb.models.errors.ErrorsException
import io.fcomb.rpc.UserResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object EditUserComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String)
  final case class FormState(email: String,
                             password: String,
                             fullName: String,
                             role: UserRole,
                             errors: Map[String, String])
  final case class State(user: Pot[UserResponse], form: Option[FormState], isDisabled: Boolean)

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
        Callback.future(Rpc.getUser(props.slug).map {
          case Right(user) =>
            val form = FormState(email = user.email,
                                 password = "",
                                 fullName = user.fullName.getOrElse(""),
                                 user.role,
                                 Map.empty)
            $.modState(st => st.copy(user = Ready(user), form = Some(form)))
          case Left(errs) => $.modState(_.copy(user = Failed(ErrorsException(errs))))
        })
      }

    def update(): Callback =
      $.props.flatMap { props =>
        tryAcquireState { state =>
          (state.user, state.form) match {
            case (Ready(user), Some(form)) =>
              Callback.future(
                Rpc
                  .updateUser(props.slug,
                              email = form.email,
                              password = StringUtils.trim(form.password),
                              fullName = StringUtils.trim(form.fullName),
                              form.role)
                  .map {
                    case Right(_)   => props.ctl.set(DashboardRoute.Users)
                    case Left(errs) => modFormState(_.copy(errors = foldErrors(errs)))
                  })
            case _ => Callback.empty
          }
        }
      }

    def handleOnSubmit(e: ReactEventH): Callback =
      e.preventDefaultCB >> update()

    def updateEmail(email: String): Callback =
      modFormState(_.copy(email = email))

    def updatePassword(password: String): Callback =
      modFormState(_.copy(password = password))

    def updateFullName(fullName: String): Callback =
      modFormState(_.copy(fullName = fullName))

    def updateRole(role: UserRole): Callback =
      modFormState(_.copy(role = role))

    def renderForm(props: Props, state: State) =
      state.form match {
        case Some(form) =>
          <.form(
            ^.onSubmit ==> handleOnSubmit,
            ^.disabled := state.isDisabled,
            ^.key := "form",
            MuiCardText(key = "form")(
              renderFormEmail(form.email, form.errors, state.isDisabled, updateEmail _),
              renderFormPassword(form.password, form.errors, state.isDisabled, updatePassword _),
              renderFormFullName(form.fullName, form.errors, state.isDisabled, updateFullName _),
              renderFormRole(form.role, form.errors, state.isDisabled, updateRole _),
              renderFormButtons(props.ctl, "Update", state.isDisabled)))
        case _ => <.div()
      }

    def render(props: Props, state: State) =
      MuiCard()(<.div(^.key := "header",
                      App.formTitleBlock,
                      MuiCardTitle(key = "title")(<.h1(App.cardTitle, "Edit user"))),
                <.section(^.key := "form", state.user.render(_ => renderForm(props, state))))
  }

  private val component = ReactComponentB[Props]("EditUser")
    .initialState(State(Empty, None, isDisabled = false))
    .renderBackend[Backend]
    .componentDidMount(_.backend.get())
    .build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String) =
    component(Props(ctl, slug))
}
