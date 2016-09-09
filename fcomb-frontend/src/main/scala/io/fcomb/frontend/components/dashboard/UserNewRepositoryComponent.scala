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
import io.fcomb.frontend.components.repository.{NewRepositoryComponent, Namespace}
import io.fcomb.frontend.dispatcher.AppCircuit
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._

object UserNewRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute])

  final class Backend($ : BackendScope[Props, Unit]) {
    def render(props: Props): ReactElement = {
      AppCircuit.currentUser match {
        case Some(user) =>
          NewRepositoryComponent.apply(props.ctl, Namespace.User(user.username, Some(user.id)))
        case _ => <.div()
      }
    }
  }

  private val component =
    ReactComponentB[Props]("UserNewRepository").renderBackend[Backend].build

  def apply(ctl: RouterCtl[DashboardRoute]) =
    component.apply(Props(ctl))
}
