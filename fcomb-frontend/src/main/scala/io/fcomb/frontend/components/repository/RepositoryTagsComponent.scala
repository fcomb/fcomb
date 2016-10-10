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
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.{
  CopyToClipboardComponent,
  PaginationOrderState,
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
  final case class State(tags: Seq[RepositoryTagResponse], pagination: PaginationOrderState)

  class Backend($ : BackendScope[Props, State]) {
    val digestLength = 12
    val limit        = 64

    def getTags(): Callback =
      $.props.zip($.state).flatMap {
        case (props, state) =>
          val p = state.pagination
          Callback.future(
            Rpc.getRepositotyTags(props.slug, p.sortColumn, p.sortOrder, p.page, limit).map {
              case Xor.Right(pd) => $.modState(_.copy(tags = pd.data))
              case Xor.Left(e)   => Callback.warn(e)
            })
      }

    def renderTagRow(props: Props, tag: RepositoryTagResponse) =
      MuiTableRow(key = tag.digest)(
        MuiTableRowColumn(key = "tag")(tag.tag),
        MuiTableRowColumn(key = "updatedAt")(TimeAgoComponent(tag.updatedAt)),
        MuiTableRowColumn(key = "length")(SizeInBytesComponent(tag.length)),
        MuiTableRowColumn(key = "digest")(
          <.div(^.title := tag.digest,
                tag.digest.take(digestLength),
                CopyToClipboardComponent(tag.digest, js.undefined, <.span("Copy")))))

    def changeSortOrder(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        slug  <- $.props.map(_.slug)
        state <- $.state
        sortOrder = {
          if (state.pagination.sortColumn == column) state.pagination.sortOrder.flip
          else state.pagination.sortOrder
        }
        _ <- $.modState(st =>
          st.copy(pagination = st.pagination.copy(sortColumn = column, sortOrder = sortOrder)))
        _ <- getTags()
      } yield ()

    def renderHeader(title: String, column: String, state: State) = {
      val header = if (state.pagination.sortColumn == column) {
        if (state.pagination.sortOrder === SortOrder.Asc) s"$title ↑"
        else s"$title ↓"
      } else title
      MuiTableHeaderColumn(key = column)(
        <.a(^.href := "#", ^.onClick ==> changeSortOrder(column), header))
    }

    // TODO
    // def renderTags(props: Props, state: State) =
    //   if (state.tags.isEmpty) <.span("No tags. Create one!")
    //   else {

    //   }

    def render(props: Props, state: State) = {
      val columns = MuiTableRow()(renderHeader("Tag", "tag", state),
                                  renderHeader("Last modified", "updatedAt", state),
                                  renderHeader("Size", "length", state),
                                  renderHeader("Image", "digest", state))
      val rows = state.tags.map(renderTagRow(props, _))
      <.section(
        MuiTable(selectable = false, multiSelectable = false)(MuiTableHeader(
                                                                adjustForCheckbox = false,
                                                                displaySelectAll = false,
                                                                enableSelectAll = false,
                                                                key = "header"
                                                              )(columns),
                                                              MuiTableBody(
                                                                deselectOnClickaway = false,
                                                                displayRowCheckbox = false,
                                                                showRowHover = false,
                                                                stripedRows = false,
                                                                key = "body"
                                                              )(rows)))
    }
  }

  private val component = ReactComponentB[Props]("RepositoryTags")
    .initialState(State(Seq.empty, PaginationOrderState(1, "updatedAt", SortOrder.Desc)))
    .renderBackend[Backend]
    .componentWillMount(_.backend.getTags())
    .build

  def apply(slug: String) =
    component.apply(Props(slug))
}
