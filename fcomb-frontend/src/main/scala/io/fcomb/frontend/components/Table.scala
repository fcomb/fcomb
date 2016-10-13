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

package io.fcomb.frontend.components

import cats.syntax.eq._
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.styles.App
import io.fcomb.models.SortOrder
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scalacss.ScalaCssReact._

object Table {
  def renderHeader(title: String,
                   column: String,
                   pagination: PaginationOrderState,
                   cb: String => ReactEventH => Callback) = {
    val (sortIcon, sortCB) =
      if (pagination.total > 1 && pagination.sortColumn == column) {
        val icon: ReactNode =
          if (pagination.sortOrder === SortOrder.Asc)
            Mui.SvgIcons.NavigationArrowUpward(style = App.sortIconStyle)()
          else Mui.SvgIcons.NavigationArrowDownward(style = App.sortIconStyle)()
        (icon, cb(column))
      } else (<.span(): ReactNode, emptyCB _)

    MuiTableHeaderColumn(key = column)(
      <.a(App.sortedColumn, ^.href := "#", ^.onClick ==> sortCB, sortIcon, <.span(title))())
  }

  private def emptyCB(e: ReactEventH) = e.preventDefaultCB
}
