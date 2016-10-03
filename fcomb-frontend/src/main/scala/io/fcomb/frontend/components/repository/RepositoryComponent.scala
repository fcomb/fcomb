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

import diode.data.PotMap
import diode.react.ModelProxy
import diode.react.ReactPot._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.components.{CopyToClipboardComponent, MarkdownComponent}
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.js

object RepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         repositories: ModelProxy[PotMap[String, RepositoryResponse]],
                         name: String)

  final class Backend($ : BackendScope[Props, Unit]) {
    def selectAllText(e: ReactEventI): Callback =
      e.preventDefaultCB >> CallbackTo(e.target.setSelectionRange(0, e.target.value.length))

    def render(props: Props): ReactElement = {
      val repository = props.repositories().get(props.name)
      println(s"repository: $repository")
      <.div(repository.renderReady { r =>
        val dockerPullCommand = s"docker pull ${props.name}"
        val description =
          if (r.description.nonEmpty) r.description
          else "*No description*"
        <.div(
          <.h2(s"Repository ${props.name}"),
          <.div(<.input.text(^.value := dockerPullCommand,
                             ^.onClick ==> selectAllText,
                             ^.readOnly := true),
                CopyToClipboardComponent.apply(dockerPullCommand, js.undefined, <.span("Copy"))),
          <.div(props.ctl.link(DashboardRoute.EditRepository(props.name))("Edit")),
          <.div(props.ctl.link(DashboardRoute.RepositoryTags(props.name))("Tags")),
          <.div(props.ctl.link(DashboardRoute.RepositorySettings(props.name))("Settings")),
          <.section(<.h3("Description"), <.article(MarkdownComponent.apply(description)))
        )
      })
    }
  }

  private val component = ReactComponentB[Props]("Repository").renderBackend[Backend].build

  def apply(ctl: RouterCtl[DashboardRoute],
            repositories: ModelProxy[PotMap[String, RepositoryResponse]],
            name: String) =
    component(Props(ctl, repositories, name))
}
