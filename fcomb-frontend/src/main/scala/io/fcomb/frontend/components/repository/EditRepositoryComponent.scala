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
import io.fcomb.frontend.styles.App
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.rpc.docker.distribution.{ImageUpdateRequest, RepositoryResponse}
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import org.scalajs.dom.raw.HTMLInputElement
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
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

    val descriptionRef = Ref[HTMLInputElement]("description")

    def applyState(props: Props) =
      props.repositories().get(props.slug) match {
        case Ready(repo) =>
          val formState = FormState(repo.visibilityKind, repo.description)
          $.modState(_.copy(form = Some(formState)))
        case _ => Callback.empty
      }

    // def formDescription(description: String)(e: ReactEventH): Callback =
    //   e.preventDefaultCB >>
    //     $.modState(_.copy(form = Some(FormState(description)))) >>
    //     CallbackTo(descriptionRef.apply($).map(_.setSelectionRange(0, 0))).delayMs(1).void

    def updateRepositoryDescription(): Callback =
      $.state.zip($.props).flatMap {
        case (state, props) =>
          state.form match {
            case Some(fs) if !state.isFormDisabled =>
              for {
                _ <- $.modState(_.copy(isFormDisabled = true))
                _ <- Callback.future {
                  val req = ImageUpdateRequest(fs.visibilityKind, fs.description)
                  Rpc.updateRepository(props.slug, req).map {
                    case Xor.Right(repository) =>
                      props.ctl.set(DashboardRoute.Repository(repository.slug))
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
      e.preventDefaultCB >> $.modState(_.copy(form = None))

    def renderActions(state: State) =
      <.div(^.`class` := "row",
            ^.style := paddingTop,
            ^.key := "actionsRow",
            <.div(^.`class` := "col-xs-12",
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
                          MuiCardTitle(key = "title")(<.h1(App.formTitle, "Edit repository"))),
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
      <.div(repository.renderReady(_ => renderForm(state)))
    }
  }

  private val component = ReactComponentB[Props]("EditRepository")
    .initialState(State(None, Map.empty, false))
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.applyState($.props))
    .componentWillReceiveProps(lc => lc.$.backend.applyState(lc.nextProps))
    .build

  def apply(ctl: RouterCtl[DashboardRoute],
            repositories: ModelProxy[PotMap[String, RepositoryResponse]],
            slug: String) =
    component(Props(ctl, repositories, slug))
}
