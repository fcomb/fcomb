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

package io.fcomb.frontend.components.dashboard

import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.components.repository.{NewRepositoryComponent, OwnerScope}
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl

object UserNewRepositoryComponent {
  private val component =
    ReactComponentB[RouterCtl[DashboardRoute]]("UserNewRepository").render_P { ctl =>
      NewRepositoryComponent.apply(ctl, OwnerScope.UserSelf)
    }.build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component.apply(ctl)
}
