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
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._

object EditRepositoryComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], name: String)
  final case class FormState(visibilityKind: ImageVisibilityKind, description: Option[String])
  final case class State(form: Option[FormState],
                         errors: Map[String, String],
                         isFormDisabled: Boolean)

  final class Backend($ : BackendScope[Props, State]) {
    def render(props: Props, state: State) = ???
  }

  private val component = ReactComponentB[Props]("EditRepository")
    .initialState(State(None, Map.empty, false))
    .renderBackend[Backend]
    // .componentWillMount($ => $.backend.getRepository($.props.name))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], name: String) =
    component(Props(ctl, name))
}
