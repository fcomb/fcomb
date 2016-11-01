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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.components.{CopyToClipboardComponent, MarkdownComponent}
import io.fcomb.frontend.styles.App
import io.fcomb.frontend.utils.RepositoryUtils
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scala.scalajs.js
import scalacss.ScalaCssReact._

object RepositoryInfoComponent {
  final case class Props(repo: RepositoryResponse)

  final class Backend($ : BackendScope[Props, Unit]) {
    def selectAllText(e: ReactEventI): Callback =
      e.preventDefaultCB >> CallbackTo(e.target.setSelectionRange(0, e.target.value.length))

    lazy val copyBtnStyle =
      js.Dictionary("position" -> "absolute", "right" -> "12px", "float" -> "right")

    def renderPullBlock(repo: RepositoryResponse) = {
      val dockerPullCommand = RepositoryUtils.dockerPullCommand(repo.slug, "latest")
      val button =
        MuiIconButton(tooltip = "Copy to clipboard", style = copyBtnStyle)(
          Mui.SvgIcons.ContentContentCopy(color = Mui.Styles.colors.lightBlack)())
      <.div(^.`class` := "col-xs-4",
            <.h4("Pull this container with Docker"),
            <.div(<.input.text(App.dockerPullInput,
                               ^.value := dockerPullCommand,
                               ^.onClick ==> selectAllText,
                               ^.readOnly := true),
                  CopyToClipboardComponent(dockerPullCommand, js.undefined, button)))
    }

    def renderDescription(repo: RepositoryResponse) = {
      val description =
        if (repo.description.nonEmpty) repo.description
        else "*No description*"
      <.div(^.`class` := "col-xs-8",
            <.h3("Description"),
            <.article(MarkdownComponent(description)))
    }

    def render(props: Props) =
      <.section(
        <.div(^.`class` := "row", renderDescription(props.repo), renderPullBlock(props.repo)))
  }

  private val component =
    ReactComponentB[Props]("RepositoryInfo").renderBackend[Backend].build

  def apply(repo: RepositoryResponse) =
    component.apply(Props(repo))
}
