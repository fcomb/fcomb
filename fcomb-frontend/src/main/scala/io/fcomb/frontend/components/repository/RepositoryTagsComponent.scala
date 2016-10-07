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
import cats.syntax.eq._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.{
  CopyToClipboardComponent,
  SizeInBytesComponent,
  TimeAgoComponent
}
import io.fcomb.models.SortOrder
import io.fcomb.rpc.docker.distribution.RepositoryTagResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object RepositoryTagsComponent {
  final case class Props(slug: String)
  final case class State(tags: Seq[RepositoryTagResponse],
                         sortColumn: String,
                         sortOrder: SortOrder)

  class Backend($ : BackendScope[Props, State]) {
    val digestLength = 12

    def getTags(slug: String, sortColumn: String, sortOrder: SortOrder): Callback =
      Callback.future(Rpc.getRepositotyTags(slug, sortColumn, sortOrder).map {
        case Xor.Right(pd) => $.modState(_.copy(tags = pd.data))
        case Xor.Left(e)   => Callback.warn(e)
      })

    def renderTagRow(props: Props, tag: RepositoryTagResponse) =
      <.tr(<.td(tag.tag),
           <.td(TimeAgoComponent.apply(tag.updatedAt)),
           <.td(SizeInBytesComponent.apply(tag.length)),
           <.td(^.title := tag.digest,
                tag.digest.take(digestLength),
                CopyToClipboardComponent.apply(tag.digest, js.undefined, <.span("Copy"))))

    def changeSortOrder(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        slug  <- $.props.map(_.slug)
        state <- $.state
        sortOrder = {
          if (state.sortColumn == column) state.sortOrder.flip
          else state.sortOrder
        }
        _ <- $.modState(_.copy(sortColumn = column, sortOrder = sortOrder))
        _ <- getTags(slug, column, sortOrder)
      } yield ()

    def renderHeader(title: String, column: String, state: State) = {
      val header = if (state.sortColumn == column) {
        if (state.sortOrder === SortOrder.Asc) s"$title ↑"
        else s"$title ↓"
      } else title
      <.th(<.a(^.href := "#", ^.onClick ==> changeSortOrder(column), header))
    }

    def renderTags(props: Props, state: State) =
      if (state.tags.isEmpty) <.span("No tags. Create one!")
      else {
        <.table(<.thead(
                  <.tr(renderHeader("Tag", "tag", state),
                       renderHeader("Last modified", "updatedAt", state),
                       renderHeader("Size", "length", state),
                       renderHeader("Image", "digest", state))),
                <.tbody(state.tags.map(renderTagRow(props, _))))
      }

    def render(props: Props, state: State) =
      <.section(renderTags(props, state))
  }

  private val component = ReactComponentB[Props]("RepositoryTags")
    .initialState(State(Seq.empty, "updatedAt", SortOrder.Desc))
    .renderBackend[Backend]
    .componentWillMount($ =>
      $.backend.getTags($.props.slug, $.state.sortColumn, $.state.sortOrder))
    .build

  def apply(slug: String) =
    component.apply(Props(slug))
}
