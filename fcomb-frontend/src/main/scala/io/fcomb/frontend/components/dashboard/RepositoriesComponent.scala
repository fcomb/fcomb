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
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.rpc.docker.distribution.ImageResponse
import io.fcomb.models.PaginationData
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.json.models.Formats._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object RepositoriesComponent {
  final case class State(repositories: Seq[ImageResponse])

  final case class Backend($ : BackendScope[Unit, State]) {
    def getRepositories() = {
      Callback.future {
        Rpc.call[PaginationData[ImageResponse]](RpcMethod.GET, Resource.userRepositories).map {
          case Xor.Right(pd) =>
            $.modState(_.copy(pd.data))
          case Xor.Left(e) =>
            println(e)
            Callback.empty
        }
      }
    }

    def renderRepository(repository: ImageResponse) = {
      <.li(repository.name)
    }

    def renderRepositories(repositories: Seq[ImageResponse]) = {
      if (repositories.isEmpty) <.span("No repositories. Create one!")
      else <.ul(repositories.map(renderRepository))
    }

    def render(state: State) = {
      <.div(<.h2("Repositories"), renderRepositories(state.repositories))
    }
  }

  private val component = ReactComponentB[Unit]("RepositoriesComponent")
    .initialState(State(Seq.empty))
    .renderBackend[Backend]
    .componentDidMount { $ ⇒
      $.backend.getRepositories()
    }
    .build

  def apply() = component.apply()
}
