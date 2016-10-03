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
import diode.data.{PotMap, Ready}
import diode.react.ModelProxy
import diode.react.ReactPot._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.Rpc
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.rpc.docker.distribution.{ImageUpdateRequest, RepositoryResponse}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.raw.HTMLInputElement
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object EditRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         repositories: ModelProxy[PotMap[String, RepositoryResponse]],
                         name: String)
  final case class FormState(visibilityKind: ImageVisibilityKind, description: String)
  final case class State(form: Option[FormState],
                         errors: Map[String, String],
                         isFormDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    val descriptionRef = Ref[HTMLInputElement]("description")

    def applyState(props: Props) =
      props.repositories().get(props.name) match {
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
                  Rpc.updateRepository(props.name, req).map {
                    case Xor.Right(repository) =>
                      props.ctl.set(DashboardRoute.Repository(repository.name))
                    case Xor.Left(e) => $.modState(_.copy(isFormDisabled = false))
                  }
                }
              } yield ()
            case _ => Callback.empty
          }
      }

    def handleOnSubmit(e: ReactEventH): Callback =
      e.preventDefaultCB >> updateRepositoryDescription

    def updateDescription(fs: FormState)(e: ReactEventI): Callback = {
      val value = e.target.value
      $.modState(_.copy(form = Some(fs.copy(description = value))))
    }

    def renderDescriptionTextArea(fs: FormState) =
      <.textarea(^.ref := descriptionRef,
                 ^.id := "description",
                 ^.name := "description",
                 ^.autoFocus := true,
                 ^.tabIndex := 1,
                 ^.rows := 24,
                 ^.cols := 120,
                 ^.value := fs.description,
                 ^.onChange ==> updateDescription(fs))

    def cancel(e: ReactEventH): Callback =
      e.preventDefaultCB >> $.modState(_.copy(form = None))

    def renderForm(formState: FormState) =
      <.span(formState.toString())

    def render(props: Props, state: State) = {
      val repository = props.repositories().get(props.name)
      <.div(
        repository.renderReady { r =>
          state.form match {
            case Some(form) => renderForm(form)
            case _          => <.div()
          }
        }
      )
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
            name: String) =
    component(Props(ctl, repositories, name))
}
