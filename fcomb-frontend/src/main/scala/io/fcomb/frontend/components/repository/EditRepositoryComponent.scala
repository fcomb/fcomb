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
import chandu0101.scalajs.react.components.materialui._
import diode.data.{PotMap, Ready}
import diode.react.ModelProxy
import diode.react.ReactPot._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.dispatcher.actions.UpsertRepository
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.styles.App
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scalacss.ScalaCssReact._

object EditRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         repositories: ModelProxy[PotMap[String, RepositoryResponse]],
                         slug: String)
  final case class FormState(visibilityKind: ImageVisibilityKind, description: String)
  final case class State(form: Option[FormState],
                         errors: Map[String, String],
                         isFormDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    import RepositoryForm._

    def applyState(props: Props) =
      props.repositories().get(props.slug) match {
        case Ready(repo) =>
          val formState = FormState(repo.visibilityKind, repo.description)
          $.modState(_.copy(form = Some(formState)))
        case _ => Callback.empty
      }

    def updateRepositoryDescription(): Callback =
      $.state.zip($.props).flatMap {
        case (state, props) =>
          state.form match {
            case Some(fs) if !state.isFormDisabled =>
              for {
                _ <- $.modState(_.copy(isFormDisabled = true))
                _ <- Callback.future {
                  Rpc.updateRepository(props.slug, fs.visibilityKind, fs.description).map {
                    case Xor.Right(repo) =>
                      AppCircuit.dispatchCB(UpsertRepository(repo)) >>
                        props.ctl.set(DashboardRoute.Repository(repo.slug))
                    case Xor.Left(e) => $.modState(_.copy(isFormDisabled = false))
                  }
                }
              } yield ()
            case _ => Callback.empty
          }
      }

    def handleOnSubmit(e: ReactEventH): Callback =
      e.preventDefaultCB >> updateRepositoryDescription

    def modFormState(f: FormState => FormState): Callback =
      $.modState(st => st.copy(form = st.form.map(f)))

    def updateVisibilityKind(e: ReactEventI, value: String): Callback = {
      val kind = ImageVisibilityKind.withName(value)
      modFormState(_.copy(visibilityKind = kind))
    }

    def updateDescription(e: ReactEventI): Callback = {
      val value = e.target.value
      modFormState(_.copy(description = value))
    }

    def cancel(e: ReactEventH): Callback =
      e.preventDefaultCB >> $.props.flatMap(p => p.ctl.set(DashboardRoute.Repository(p.slug)))

    lazy val cancelStyle = js.Dictionary("marginRight" -> App.paddingTopLength)

    def renderActions(state: State) =
      <.div(^.`class` := "row",
            ^.style := App.paddingTopStyle,
            ^.key := "actionsRow",
            <.div(^.`class` := "col-xs-12",
                  MuiRaisedButton(`type` = "button",
                                  primary = false,
                                  label = "Cancel",
                                  style = cancelStyle,
                                  disabled = state.isFormDisabled,
                                  onTouchTap = cancel _,
                                  key = "cancel")(),
                  MuiRaisedButton(`type` = "submit",
                                  primary = true,
                                  label = "Update",
                                  disabled = state.isFormDisabled,
                                  key = "update")()))

    def renderForm(state: State): ReactNode =
      state.form match {
        case Some(form) =>
          MuiCard()(<.div(^.key := "header",
                          App.formTitleBlock,
                          MuiCardTitle(key = "title")(<.h1(App.cardTitle, "Edit repository"))),
                    <.form(^.onSubmit ==> handleOnSubmit,
                           ^.disabled := state.isFormDisabled,
                           ^.key := "form",
                           MuiCardText(key = "form")(renderDescription(form.description,
                                                                       state.errors,
                                                                       state.isFormDisabled,
                                                                       updateDescription),
                                                     renderVisiblity(form.visibilityKind,
                                                                     state.isFormDisabled,
                                                                     updateVisibilityKind),
                                                     renderActions(state))))
        case _ => <.div()
      }

    def render(props: Props, state: State) = {
      val repository = props.repositories().get(props.slug)
      <.div(repository.render(_ => renderForm(state)))
    }
  }

  private val component = ReactComponentB[Props]("EditRepository")
    .initialState(State(None, Map.empty, false))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.applyState($.props))
    .componentWillReceiveProps(lc => lc.$.backend.applyState(lc.nextProps))
    .build

  def apply(ctl: RouterCtl[DashboardRoute],
            repositories: ModelProxy[PotMap[String, RepositoryResponse]],
            slug: String) =
    component(Props(ctl, repositories, slug))
}
