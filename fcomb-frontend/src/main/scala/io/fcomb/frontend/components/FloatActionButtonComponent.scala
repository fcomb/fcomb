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

import chandu0101.scalajs.react.components.materialui.Mui.SvgIcons.ContentAdd
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scalacss.ScalaCssReact._

object FloatActionButtonComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         route: DashboardRoute,
                         title: String,
                         icon: ReactNode)

  class Backend($ : BackendScope[Props, Unit]) {
    def setRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(route))

    def render(props: Props) =
      <.div(App.floatActionButton,
            ^.title := props.title,
            ^.key := "fab",
            MuiFloatingActionButton(secondary = true, onTouchTap = setRoute(props.route) _)(
              props.icon))
  }

  private val component =
    ReactComponentB[Props]("FloatActionButton").renderBackend[Backend].build

  def apply(ctl: RouterCtl[DashboardRoute],
            route: DashboardRoute,
            title: String,
            icon: ReactNode = ContentAdd()()) =
    component.apply(Props(ctl, route, title, icon))
}
