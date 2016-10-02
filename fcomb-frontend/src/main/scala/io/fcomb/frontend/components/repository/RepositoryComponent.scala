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
import diode.data._
import diode.react.ModelProxy
import diode.react.ReactPot._
import diode._
import io.fcomb.frontend.components.{CopyToClipboardComponent, MarkdownComponent}
import io.fcomb.frontend.DashboardRoute
import io.fcomb.rpc.docker.distribution.{ImageUpdateRequest, RepositoryResponse}
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import org.scalajs.dom.raw.HTMLInputElement
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object RepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         repositories: ModelProxy[PotMap[String, RepositoryResponse]],
                         name: String)

  final class Backend($ : BackendScope[Props, Unit]) {
    // val descriptionRef = Ref[HTMLInputElement]("description")

    def selectAllText(e: ReactEventI): Callback =
      e.preventDefaultCB >> CallbackTo(e.target.setSelectionRange(0, e.target.value.length))

    // def formDescription(description: String)(e: ReactEventH): Callback =
    //   e.preventDefaultCB >>
    //     $.modState(_.copy(form = Some(FormState(description, false, false)))) >>
    //     CallbackTo(descriptionRef.apply($).map(_.setSelectionRange(0, 0))).delayMs(1).void
    //
    // def updateRepositoryDescription(): Callback =
    //   formState { fs =>
    //     if (fs.isFormDisabled) Callback.empty
    //     for {
    //       _    <- $.modState(_.copy(form = Some(fs.copy(isFormDisabled = true))))
    //       name <- $.props.map(_.name)
    //       _ <- Callback.future {
    //         val req = ImageUpdateRequest(fs.description)
    //         Rpc
    //           .updateRepository(name, req)
    //           .map {
    //             case Xor.Right(repository) =>
    //               $.modState(_.copy(repository = Some(repository), form = None))
    //             case Xor.Left(e) =>
    //               $.modState(_.copy(form = Some(fs.copy(isFormDisabled = false))))
    //           }
    //           .recover {
    //             case _ => $.modState(_.copy(form = Some(fs.copy(isFormDisabled = false))))
    //           }
    //       }
    //     } yield ()
    //   }
    //
    // def handleOnSubmit(e: ReactEventH): Callback =
    //   e.preventDefaultCB >> updateRepositoryDescription
    //
    // def updateDescription(fs: FormState)(e: ReactEventI): Callback = {
    //   val value = e.target.value
    //   $.modState(_.copy(form = Some(fs.copy(description = value))))
    // }
    //
    // def formState(f: FormState => Callback): Callback =
    //   $.state.flatMap { state =>
    //     state.form match {
    //       case Some(fs) => f(fs)
    //       case None     => Callback.empty
    //     }
    //   }
    //
    // def switchToPreview(isPreview: Boolean)(e: ReactEventH): Callback =
    //   e.preventDefaultCB >> formState { fs =>
    //     if (fs.isPreview == isPreview) Callback.empty
    //     else $.modState(_.copy(form = Some(fs.copy(isPreview = isPreview))))
    //   }
    //
    // def renderDescriptionTextArea(fs: FormState) =
    //   if (fs.isPreview) EmptyTag
    //   else
    //     <.textarea(^.ref := descriptionRef,
    //                ^.id := "description",
    //                ^.name := "description",
    //                ^.autoFocus := true,
    //                ^.tabIndex := 1,
    //                ^.rows := 24,
    //                ^.cols := 120,
    //                ^.value := fs.description,
    //                ^.onChange ==> updateDescription(fs))
    //
    // def renderPreview(fs: FormState) =
    //   if (fs.isPreview) <.article(MarkdownComponent.apply(fs.description))
    //   else EmptyTag
    //
    // def cancel(e: ReactEventH): Callback =
    //   e.preventDefaultCB >> $.modState(_.copy(form = None))

    def render(props: Props): ReactElement = {
      val repository = props.repositories().get(props.name)
      <.div(repository.renderReady { r =>
        val dockerPullCommand = s"docker pull ${props.name}"
        <.div(
          <.h2(s"Repository ${props.name}"),
          <.div(<.input.text(^.value := dockerPullCommand,
                             ^.onClick ==> selectAllText,
                             ^.readOnly := true),
                CopyToClipboardComponent.apply(dockerPullCommand, js.undefined, <.span("Copy"))),
          <.div(props.ctl.link(DashboardRoute.RepositoryTags(props.name))("Tags")),
          <.div(props.ctl.link(DashboardRoute.RepositorySettings(props.name))("Settings")),
          <.section(<.h3("Description"), <.article(MarkdownComponent.apply(r.description)))
        )
      })
    }
  }

  private val component = ReactComponentB[Props]("Repository").renderBackend[Backend].build

  import io.fcomb.frontend.dispatcher.AppCircuit

  def apply(ctl: RouterCtl[DashboardRoute], name: String) =
    AppCircuit.repositories { rs =>
      println(s"apply: $rs")
      component(Props(ctl, rs, name))
    }
}
