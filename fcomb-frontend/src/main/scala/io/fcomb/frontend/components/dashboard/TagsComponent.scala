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
import cats.syntax.eq._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.{PaginationData, SortOrder}
import io.fcomb.rpc.docker.distribution.RepositoryTagResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object TagsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], repositoryName: String)
  final case class State(tags: Seq[RepositoryTagResponse],
                         sortColumn: String,
                         sortOrder: SortOrder)

  final case class Backend($ : BackendScope[Props, State]) {
    private def getTagsCB(name: String, sortColumn: String, sortOrder: SortOrder): Callback = {
      Callback.future {
        val queryParams = SortOrder.toQueryParams(Seq((sortColumn, sortOrder)))
        Rpc
          .call[PaginationData[RepositoryTagResponse]](RpcMethod.GET,
                                                       Resource.repositoryTags(name),
                                                       queryParams = queryParams)
          .map {
            case Xor.Right(pd) =>
              $.modState(_.copy(tags = pd.data))
            case Xor.Left(e) =>
              println(e)
              Callback.empty
          }
      }
    }

    def getTags(): Callback = {
      for {
        name  <- $.props.map(_.repositoryName)
        state <- $.state
        _     <- getTagsCB(name, state.sortColumn, state.sortOrder)
      } yield ()
    }

    def renderTagRow(props: Props, tag: RepositoryTagResponse) = {
      // <.li(ctl.link(DashboardRoute.Repository(props.repositoryName, tag.tag))(tag.tag))
      <.tr(<.td(tag.tag),
           <.td(TimeAgoComponent.apply(tag.updatedAt)),
           <.td(SizeInBytesComponent.apply(tag.length)),
           <.td(tag.imageSha256Digest))
    }

    def changeSortOrder(column: String)(e: ReactEventH): Callback = {
      for {
        _     <- e.preventDefaultCB
        name  <- $.props.map(_.repositoryName)
        state <- $.state
        sortOrder = {
          if (state.sortColumn == column) {
            state.sortOrder match {
              case SortOrder.Asc => SortOrder.Desc
              case _             => SortOrder.Asc
            }
          } else state.sortOrder
        }
        _ <- $.modState(_.copy(sortColumn = column, sortOrder = sortOrder))
        _ <- getTagsCB(name, column, sortOrder)
      } yield ()
    }

    def renderHeader(title: String, column: String, state: State) = {
      val header = if (state.sortColumn == column) {
        if (state.sortOrder === SortOrder.Asc) s"$title ↑"
        else s"$title ↓"
      } else title
      <.th(<.a(^.href := "#", ^.onClick ==> changeSortOrder(column), header))
    }

    def renderTags(props: Props, state: State) = {
      if (state.tags.isEmpty) <.span("No tags. Create one!")
      else {
        <.table(<.thead(
                  <.tr(renderHeader("Tag", "tag", state),
                       renderHeader("Last modified", "updatedAt", state),
                       renderHeader("Size", "length", state),
                       renderHeader("Image", "imageSha256Digest", state))),
                <.tbody(state.tags.map(renderTagRow(props, _))))
      }
    }

    def render(props: Props, state: State) = {
      <.div(<.h2("Tags"), renderTags(props, state))
    }
  }

  private val component = ReactComponentB[Props]("TagsComponent")
    .initialState(State(Seq.empty, "updatedAt", SortOrder.Desc))
    .renderBackend[Backend]
    .componentDidMount(_.backend.getTags())
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String) =
    component.apply(Props(ctl, repositoryName))
}
