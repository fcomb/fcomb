/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

package io.fcomb.frontend.components

import cats.syntax.eq._
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.styles.App
import io.fcomb.models.SortOrder
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.js
import scalacss.ScalaCssReact._

object TableComponent {
  def header(title: String,
             column: String,
             pagination: PaginationOrderState,
             cb: String => ReactEventH => Callback,
             style: js.Dictionary[String] = js.Dictionary.empty) = {
    val (sortIcon, sortCB) =
      if (pagination.total > 1) {
        val icon: ReactNode =
          if (pagination.sortColumn == column) {
            if (pagination.sortOrder === SortOrder.Asc)
              Mui.SvgIcons.NavigationArrowDropUp(style = App.sortIconStyle)()
            else Mui.SvgIcons.NavigationArrowDropDown(style = App.sortIconStyle)()
          } else <.span()
        (icon, cb(column))
      } else (<.span(): ReactNode, emptyCB _)

    MuiTableHeaderColumn(style = style, key = column)(
      <.a(App.sortedColumn, ^.href := "#", ^.onClick ==> sortCB, <.span(title), sortIcon)())
  }

  def apply(columns: Seq[ReactComponentU_],
            rows: Seq[ReactComponentU_],
            page: Int,
            limit: Int,
            total: Int,
            cb: Int => Callback) =
    <.div(
      ^.key := "table",
      MuiTable(selectable = false, multiSelectable = false, key = "table")(
        MuiTableHeader(
          adjustForCheckbox = false,
          displaySelectAll = false,
          enableSelectAll = false,
          key = "header"
        )(MuiTableRow()(columns)),
        MuiTableBody(
          deselectOnClickaway = false,
          displayRowCheckbox = false,
          showRowHover = false,
          stripedRows = false,
          key = "body"
        )(rows)
      ),
      ToolbarPaginationComponent(page, limit, total, cb)
    )

  private def emptyCB(e: ReactEventH) = e.preventDefaultCB
}
