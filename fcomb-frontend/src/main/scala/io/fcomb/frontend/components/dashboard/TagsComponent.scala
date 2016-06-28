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
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.PaginationData
import io.fcomb.rpc.docker.distribution.RepositoryTagResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object TagsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], repositoryName: String)
  final case class State(tags: Seq[RepositoryTagResponse])

  final case class Backend($ : BackendScope[Props, State]) {
    def getTags() = {
      for {
        name <- $.props.map(_.repositoryName)
        _ <- Callback.future {
              Rpc
                .call[PaginationData[RepositoryTagResponse]](RpcMethod.GET,
                                                             Resource.repositoryTags(name))
                .map {
                  case Xor.Right(pd) =>
                    $.modState(_.copy(pd.data))
                  case Xor.Left(e) =>
                    println(e)
                    Callback.empty
                }
            }
      } yield ()
    }

    def renderTagRow(props: Props, tag: RepositoryTagResponse) = {
      // <.li(ctl.link(DashboardRoute.Repository(props.repositoryName, tag.tag))(tag.tag))
      <.tr(<.td(tag.tag), <.td(tag.updatedAt), <.td(tag.length), <.td(tag.imageSha256Digest))
    }

    def renderTags(props: Props, tags: Seq[RepositoryTagResponse]) = {
      if (tags.isEmpty) <.span("No tags. Create one!")
      else {
        <.table(<.thead(<.tr(<.th("Tag"), <.th("Last modified"), <.th("Size"), <.th("Image"))),
                <.tbody(tags.map(renderTagRow(props, _))))
      }
    }

    def render(props: Props, state: State) = {
      <.div(<.h2("Tags"), renderTags(props, state.tags))
    }
  }

  private val component = ReactComponentB[Props]("TagsComponent")
    .initialState(State(Seq.empty))
    .renderBackend[Backend]
    .componentDidMount { $ ⇒
      $.backend.getTags()
    }
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String) =
    component.apply(Props(ctl, repositoryName))
}
