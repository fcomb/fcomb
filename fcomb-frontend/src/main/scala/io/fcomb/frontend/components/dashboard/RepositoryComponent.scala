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

package io.fcomb.frontend.components.dashboard

import cats.data.Xor
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.rpc.docker.distribution.{ImageResponse, ImageUpdateRequest}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import org.scalajs.dom.raw.HTMLInputElement
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object RepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], name: String)
  final case class EditState(description: String, isPreview: Boolean, isFormDisabled: Boolean)
  final case class State(repository: Option[ImageResponse], edit: Option[EditState])

  final case class Backend($ : BackendScope[Props, State]) {
    val textarea = Ref[HTMLInputElement]("description")

    def getRepository(name: String) = {
      Callback.future {
        Rpc.call[ImageResponse](RpcMethod.GET, Resource.repository(name)).map {
          case Xor.Right(repository) =>
            $.modState(_.copy(repository = Some(repository)))
          case Xor.Left(e) =>
            println(e)
            Callback.empty
        }
      }
    }

    def selectAllText(e: ReactEventI) = {
      e.preventDefaultCB >> CallbackTo(e.target.setSelectionRange(0, e.target.value.length))
    }

    def editDescription(description: String)(e: ReactEventH) = {
      e.preventDefaultCB >>
      $.modState(_.copy(edit = Some(EditState(description, false, false)))) >>
      CallbackTo(textarea.apply($).map(_.setSelectionRange(0, 0))).delayMs(1).void
    }

    def updateRepositoryDescription(): Callback = {
      eventState { es =>
        if (es.isFormDisabled) Callback.empty
        for {
          _    <- $.modState(_.copy(edit = Some(es.copy(isFormDisabled = true))))
          name <- $.props.map(_.name)
          _ <- Callback.future {
                val req = ImageUpdateRequest(es.description)
                Rpc
                  .callWith[ImageUpdateRequest, ImageResponse](RpcMethod.PUT,
                                                               Resource.repository(name),
                                                               req)
                  .map {
                    case Xor.Right(repository) =>
                      $.modState(_.copy(repository = Some(repository), edit = None))
                    case Xor.Left(e) =>
                      $.modState(_.copy(edit = Some(es.copy(isFormDisabled = false))))
                  }
                  .recover {
                    case _ => $.modState(_.copy(edit = Some(es.copy(isFormDisabled = false))))
                  }
              }
        } yield ()
      }
    }

    def handleOnSubmit(e: ReactEventH) = {
      e.preventDefaultCB >> updateRepositoryDescription
    }

    def updateDescription(es: EditState)(e: ReactEventI) = {
      val value = e.target.value
      $.modState(_.copy(edit = Some(es.copy(description = value))))
    }

    def eventState(f: EditState => CallbackTo[Unit]): CallbackTo[Unit] = {
      $.state.flatMap { state =>
        state.edit match {
          case Some(es) => f(es)
          case None     => Callback.empty
        }
      }
    }

    def switchToPreview(isPreview: Boolean)(e: ReactEventH) = {
      e.preventDefaultCB >> eventState { es =>
        if (es.isPreview == isPreview) Callback.empty
        else $.modState(_.copy(edit = Some(es.copy(isPreview = isPreview))))
      }
    }

    def renderTextarea(es: EditState) = {
      if (es.isPreview) EmptyTag
      else
        <.textarea(^.ref := textarea,
                   ^.id := "description",
                   ^.name := "description",
                   ^.autoFocus := true,
                   ^.tabIndex := 1,
                   ^.rows := 24,
                   ^.cols := 120,
                   ^.value := es.description,
                   ^.onChange ==> updateDescription(es))
    }

    def renderPreview(es: EditState) = {
      if (es.isPreview) <.article(MarkdownComponent.apply(es.description))
      else EmptyTag
    }

    def cancel(e: ReactEventH) = {
      e.preventDefaultCB >> $.modState(_.copy(edit = None))
    }

    def renderDescription(state: State) = {
      state.edit match {
        case Some(es) =>
          <.form(^.onSubmit ==> handleOnSubmit,
                 ^.disabled := es.isFormDisabled,
                 <.div(
                   <.a(^.onClick ==> switchToPreview(false), ^.href := "#", "Edit"),
                   "|",
                   <.a(^.onClick ==> switchToPreview(true), ^.href := "#", "Preview")
                 ),
                 renderTextarea(es),
                 renderPreview(es),
                 <.br,
                 <.button(^.`type` := "button", ^.tabIndex := 2, ^.onClick ==> cancel, "Cancel"),
                 <.input.submit(^.tabIndex := 3, ^.value := "Update"))
        case None =>
          val description = state.repository.map(_.description).getOrElse("")
          <.div(
            <.a(^.onClick ==> editDescription(description), ^.href := "#", "Edit"),
            <.article(MarkdownComponent.apply(description))
          )
      }
    }

    def render(props: Props, state: State) = {
      val dockerPullCommand = s"docker pull ${props.name}"
      <.div(
        <.h2(s"Repository ${props.name}"),
        <.div(<.input.text(^.value := dockerPullCommand,
                           ^.onClick ==> selectAllText,
                           ^.readOnly := true),
              CopyToClipboardComponent.apply(dockerPullCommand, js.undefined, <.span("copy"))),
        <.section(<.h3("Description"), renderDescription(state))
      )
    }
  }

  private val component = ReactComponentB[Props]("RepositoryComponent")
    .initialState(State(None, None))
    .renderBackend[Backend]
    .componentDidMount { $ ⇒
      $.backend.getRepository($.props.name)
    }
    .build

  def apply(ctl: RouterCtl[DashboardRoute], name: String) =
    component(Props(ctl, name))
}
