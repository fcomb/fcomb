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

package io.fcomb.frontend.components.organization

import io.fcomb.frontend.DashboardRoute
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._

object OrganizationSettingsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], orgName: String)

  final class Backend($ : BackendScope[Props, Unit]) {
    def render(props: Props) =
      <.section(DeleteOrganizationComponent.apply(props.ctl, props.orgName))
  }

  private val component =
    ReactComponentB[Props]("OrganizationSettings").renderBackend[Backend].build

  def apply(ctl: RouterCtl[DashboardRoute], orgName: String) =
    component.apply(Props(ctl, orgName))
}
