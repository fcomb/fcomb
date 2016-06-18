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

import io.fcomb.frontend.Route
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router._
import org.scalajs.dom.window

object RouterComponent {
  val baseUrl = BaseUrl(window.location.href.takeWhile(_ != '#'))

  val routerConfig: RouterConfig[Route] = RouterConfigDsl[Route].buildConfig { dsl =>
    import dsl._

    val routes =
      trimSlashes | staticRoute(root, Route.SignIn) ~> renderR(ctl => SignInComponent.apply(ctl))

    routes.notFound(redirectToPage(Route.SignIn)(Redirect.Replace))
  }

  private val component = Router(baseUrl, routerConfig.logToConsole)

  def apply(): ReactComponentU[Unit, Resolution[Route], Any, TopNode] =
    component.apply()
}
