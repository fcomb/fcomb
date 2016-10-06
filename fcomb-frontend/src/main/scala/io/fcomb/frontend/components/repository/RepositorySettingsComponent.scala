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

package io.fcomb.frontend.components.repository

import io.fcomb.frontend.DashboardRoute
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import japgolly.scalajs.react._

object RepositorySettingsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: String)

  class Backend($ : BackendScope[Props, Unit]) {
    def render(props: Props): ReactElement =
      <.section(<.h1("Settings"), DeleteRepositoryComponent.apply(props.ctl, props.slug))
  }

  private val component = ReactComponentB[Props]("RepositorySettings").renderBackend[Backend].build

  def apply(ctl: RouterCtl[DashboardRoute], slug: String) =
    component.apply(Props(ctl, slug))
}
