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

import japgolly.scalajs.react._
import scala.scalajs.js

object CopyToClipboardComponent {
  def apply(text: String,
            onCopy: js.UndefOr[() => Unit],
            children: ReactNode,
            key: String = "copyToClipboard") = {
    val props = js.Dynamic.literal()
    props.updateDynamic("text")(text)
    props.updateDynamic("key")(key)
    onCopy.foreach(v => props.updateDynamic("onCopy")(v))
    val f = React.asInstanceOf[js.Dynamic].createFactory(js.Dynamic.global.ReactCopyToClipboard)
    f(props, children).asInstanceOf[ReactComponentU_]
  }
}
