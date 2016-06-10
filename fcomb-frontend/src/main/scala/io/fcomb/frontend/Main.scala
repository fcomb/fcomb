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

package io.fcomb.frontend

import scala.scalajs.js.JSApp
import org.scalajs.dom.document
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scalacss.Defaults._
import scalacss.ScalaCssReact._

object Main extends JSApp {
  val MyComponent = ReactComponentB[Unit]("comp")
    .render(_ => <.button(^.onClick --> Callback.alert("wow!"), Styles.button, "click me"))
    .build

  def main(): Unit = {
    Styles.addToDocument()
    MyComponent().render(document.getElementById("container"))
  }
}

object Styles extends StyleSheet.Inline {
  import dsl._

  val button = style(
    addClassNames("btn", "btn-default")
  )
}
