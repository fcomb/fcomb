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

import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.styles.App
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._
import scalacss.ScalaCssReact._

object BreadcrumbsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute],
                         breadcrumbs: Seq[(String, DashboardRoute)],
                         icon: Option[ReactNode])

  final class Backend($ : BackendScope[Props, Unit]) {
    def breadcrumbLink(ctl: RouterCtl[DashboardRoute], target: DashboardRoute, text: String) =
      <.a(LayoutComponent.linkAsTextStyle,
          ^.href := ctl.urlFor(target).value,
          ctl.setOnLinkClick(target))(text)

    lazy val slashElement: TagMod = " / "

    def render(props: Props) = {
      val breadcrumbs = props.breadcrumbs.foldRight(List.empty[TagMod]) {
        case ((text, route), xs) =>
          val link = breadcrumbLink(props.ctl, route, text)
          if (xs.isEmpty) List(link) else link :: slashElement :: xs
      }
      <.h1(App.cardTitle, props.icon, breadcrumbs)
    }
  }

  private val component =
    ReactComponentB[Props]("Breadcrumbs").renderBackend[Backend].build

  def apply(ctl: RouterCtl[DashboardRoute],
            breadcrumbs: Seq[(String, DashboardRoute)],
            icon: Option[ReactNode] = None) =
    component.apply(Props(ctl, breadcrumbs, icon))
}
