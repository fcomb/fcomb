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
  TimeAgoComponent,
  ToolbarPaginationComponent
}
import io.fcomb.frontend.styles.App
import io.fcomb.models.SortOrder
import io.fcomb.rpc.docker.distribution.RepositoryTagResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scalacss.ScalaCssReact._

object RepositoryTagsComponent {
  final case class Props(slug: String)
  final case class State(tags: Seq[RepositoryTagResponse], pagination: PaginationOrderState) {
    def flipSortColumn(column: String): State = {
      val sortOrder =
        if (pagination.sortColumn == column) pagination.sortOrder.flip
        else pagination.sortOrder
      this.copy(pagination = pagination.copy(sortColumn = column, sortOrder = sortOrder))
    }
  }

  class Backend($ : BackendScope[Props, State]) {
    val digestLength = 12
    val limit        = 25

    def getTags(pos: PaginationOrderState): Callback =
      $.props.flatMap { props =>
        Callback.future(
          Rpc.getRepositotyTags(props.slug, pos.sortColumn, pos.sortOrder, pos.page, limit).map {
            case Xor.Right(pd) =>
              $.modState(st =>
                st.copy(tags = pd.data, pagination = st.pagination.copy(total = pd.total)))
            case Xor.Left(e) => Callback.warn(e)
          })
      }

    def renderTagRow(props: Props, tag: RepositoryTagResponse) =
      MuiTableRow(key = tag.tag)(
        MuiTableRowColumn(key = "tag")(tag.tag),
        MuiTableRowColumn(key = "updatedAt")(TimeAgoComponent(tag.updatedAt)),
        MuiTableRowColumn(key = "length")(SizeInBytesComponent(tag.length)),
        MuiTableRowColumn(key = "digest")(
          <.div(^.title := tag.digest, tag.digest.take(digestLength))),
        MuiTableRowColumn(key = "actions")(
          CopyToClipboardComponent(tag.digest, js.undefined, <.span("Copy"))))

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        state <- $.state.map(_.flipSortColumn(column))
        _     <- $.setState(state)
        _     <- getTags(state.pagination)
      } yield ()

    def updatePage(page: Int): Callback =
      $.state.flatMap { state =>
        val pagination = state.pagination.copy(page = page)
        $.setState(state.copy(pagination = pagination)) >> getTags(pagination)
      }

    lazy val sortIconStyle = js.Dictionary("width" -> "21px",
                                           "height"        -> "21px",
                                           "paddingRight"  -> "8px",
                                           "verticalAlign" -> "middle")

    def renderHeader(title: String, column: String, state: State) = {
      val sortIcon: ReactNode =
        if (state.pagination.sortColumn == column) {
          if (state.pagination.sortOrder === SortOrder.Asc)
            Mui.SvgIcons.NavigationArrowUpward(style = sortIconStyle)()
          else Mui.SvgIcons.NavigationArrowDownward(style = sortIconStyle)()
        } else <.span()

      MuiTableHeaderColumn(key = column)(
        <.a(App.sortedColumn,
            ^.href := "#",
            ^.onClick ==> updateSort(column),
            sortIcon,
            <.span(title))())
    }

    def render(props: Props, state: State) =
      if (state.tags.isEmpty) <.div(App.infoMsg, "There are no tags to show yet")
      else {
        val columns = MuiTableRow()(renderHeader("Tag", "tag", state),
                                    renderHeader("Last modified", "updatedAt", state),
                                    renderHeader("Size", "length", state),
                                    renderHeader("Image", "digest", state),
                                    MuiTableHeaderColumn(key = "actions")())
        val rows = state.tags.map(renderTagRow(props, _))
        val p    = state.pagination
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
                                                                )(rows)),
          ToolbarPaginationComponent(p.page, limit, p.total, updatePage _))
      }
  }

  private val component = ReactComponentB[Props]("RepositoryTags")
    .initialState(State(Seq.empty, PaginationOrderState("updatedAt", SortOrder.Desc)))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getTags($.state.pagination))
    .build

  def apply(slug: String) =
    component.apply(Props(slug))
}
