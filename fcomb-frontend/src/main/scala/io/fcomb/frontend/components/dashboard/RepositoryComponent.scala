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
import io.fcomb.rpc.docker.distribution.ImageResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object RepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], name: String)
  final case class State(repository: Option[ImageResponse])

  final case class Backend($ : BackendScope[Props, State]) {
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

    def render(props: Props, state: State) = {
      val description = state.repository.map(_.description).getOrElse("")
      <.div(
        <.h2(s"Repository ${props.name}"),
        <.section(<.h3("Description"), <.div(MarkdownComponent.apply(description)))
      )
    }
  }

  private val component = ReactComponentB[Props]("RepositoryComponent")
    .initialState(State(None))
    .renderBackend[Backend]
    .componentDidMount { $ ⇒
      $.backend.getRepository($.props.name)
    }
    .build

  def apply(ctl: RouterCtl[DashboardRoute], name: String) =
    component(Props(ctl, name))
}
