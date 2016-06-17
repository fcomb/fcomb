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

import io.fcomb.frontend.components._
import io.fcomb.frontend.styles._
import org.scalajs.dom.document
import scala.scalajs.js.JSApp
import scalacss.Defaults._
import scalacss.ScalaCssReact._
import scalacss.mutable.GlobalRegistry

object Main extends JSApp {
  def main(): Unit = {
    GlobalRegistry.register(Global)
    GlobalRegistry.addToDocumentOnRegistration()

    SignInComponent.component().render(document.getElementById("app"))
  }
}
