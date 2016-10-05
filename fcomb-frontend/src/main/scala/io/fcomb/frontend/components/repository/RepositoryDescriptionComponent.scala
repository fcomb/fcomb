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

import io.fcomb.frontend.components.MarkdownComponent
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._

object RepositoryDescriptionComponent {
  final case class Props(repo: RepositoryResponse)

  class Backend($ : BackendScope[Props, Unit]) {
    def render(props: Props) = {
      val description =
        if (props.repo.description.nonEmpty) props.repo.description
        else "*No description*"
      <.section(<.h3("Description"), <.article(MarkdownComponent(description)))
    }
  }

  private val component =
    ReactComponentB[Props]("RepositoryDescription").renderBackend[Backend].build

  def apply(repo: RepositoryResponse) =
    component.apply(Props(repo))
}
