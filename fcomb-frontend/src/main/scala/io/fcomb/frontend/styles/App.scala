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

package io.fcomb.frontend.styles

import scalacss.Defaults._

object App extends StyleSheet.Inline {
  import dsl._

  val body = style(
    backgroundColor(c"#eee"),
    fontFamily :=! "Roboto, sans-serif"
  )

  val floatActionButton = style(
    transform := "translateY(0)!important",
    backfaceVisibility.hidden,
    position.fixed,
    right(24.px),
    bottom(24.px),
    transition := "transform .3s cubic-bezier(.4,0,.2,1),-webkit-transform .3s cubic-bezier(.4,0,.2,1)",
    zIndex(21))

  val main = style(paddingTop(48.px))

  val lightBlack = rgba(0, 0, 0, 0.87)

  val sortedColumn = style(color(lightBlack), textDecoration := "none")

  val helpBlock = style(color(c"#888"))

  val cardTitleBlock = style(padding(0.px), position.relative, boxSizing.borderBox, margin(0.px))

  val formTitleBlock = cardTitleBlock + style(borderBottom :=! "1px solid #eee")

  val cardTitle = style(fontWeight :=! "400", margin(0.px), fontSize(20.px))

  val rightActionBlock = style(position.absolute, margin(0.px), top(-12.px), right(0.px))

  val footer = style(textAlign.center,
                     paddingTop(24.px),
                     paddingBottom(12.px),
                     position.absolute,
                     // bottom(0.px),
                     right(0.px),
                     left(0.px))

  val footerLink = style(textDecoration := "none", fontSize(13.px))
}
