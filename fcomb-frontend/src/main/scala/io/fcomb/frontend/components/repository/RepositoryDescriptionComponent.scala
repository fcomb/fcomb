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

import io.fcomb.frontend.components.{CopyToClipboardComponent, MarkdownComponent}
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.js

object RepositoryDescriptionComponent {
  final case class Props(repo: RepositoryResponse)

  class Backend($ : BackendScope[Props, Unit]) {
    def selectAllText(e: ReactEventI): Callback =
      e.preventDefaultCB >> CallbackTo(e.target.setSelectionRange(0, e.target.value.length))

    def render(props: Props) = {
      val dockerPullCommand = s"docker pull ${props.repo.slug}"
      val description =
        if (props.repo.description.nonEmpty) props.repo.description
        else "*No description*"
      <.section(
        <.h3("Description"),
        <.div(<.input.text(^.value := dockerPullCommand,
                           ^.onClick ==> selectAllText,
                           ^.readOnly := true),
              CopyToClipboardComponent.apply(dockerPullCommand, js.undefined, <.span("Copy"))),
        <.article(MarkdownComponent(description)))
    }
  }

  private val component =
    ReactComponentB[Props]("RepositoryDescription").renderBackend[Backend].build

  def apply(repo: RepositoryResponse) =
    component.apply(Props(repo))
}
