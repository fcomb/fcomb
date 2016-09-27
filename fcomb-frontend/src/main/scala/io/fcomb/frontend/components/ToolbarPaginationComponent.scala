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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.js

object ToolbarPaginationComponent {
  final case class Props(page: Int, limit: Int, total: Int, cb: Int => Callback)

  class Backend($ : BackendScope[Props, Unit]) {
    def paginate(page: Int)(e: ReactTouchEventH): Callback =
      $.props.flatMap(_.cb(page))

    val color = Mui.Styles.colors.lightBlack

    def render(props: Props): ReactElement =
      if (props.limit >= props.total) <.div()
      else {
        val page  = props.page
        val pages = math.ceil(props.total.toDouble / props.limit).toInt
        MuiToolbar(style = js.Dictionary("height" -> "48px"))(
          MuiToolbarGroup(firstChild = true, key = "right")(),
          MuiToolbarGroup(lastChild = true, key = "left")(
            MuiIconButton(disabled = page == 1, onTouchTap = paginate(1) _, key = "first")(
              Mui.SvgIcons.NavigationFirstPage(color = color)()),
            MuiIconButton(disabled = page == 1, onTouchTap = paginate(page - 1) _, key = "prev")(
              Mui.SvgIcons.NavigationChevronLeft(color = color)()),
            MuiToolbarTitle(style = js.Dictionary("paddingRight" -> "0",
                                                  "lineHeight"   -> "48px",
                                                  "fontSize"     -> "1em",
                                                  "color"        -> color.toString),
                            key = "page",
                            text = s"${props.page} of ${pages}")(),
            MuiIconButton(disabled = page == pages,
                          onTouchTap = paginate(page + 1) _,
                          key = "next")(Mui.SvgIcons.NavigationChevronRight(color = color)()),
            MuiIconButton(disabled = page == pages, onTouchTap = paginate(pages) _, key = "last")(
              Mui.SvgIcons.NavigationLastPage(color = color)())
          )
        )
      }
  }

  private val component = ReactComponentB[Props]("ToolbarPagination").renderBackend[Backend].build

  def apply(page: Int, limit: Int, total: Int, cb: Int => Callback) =
    component(new Props(page, limit, total, cb))
}
