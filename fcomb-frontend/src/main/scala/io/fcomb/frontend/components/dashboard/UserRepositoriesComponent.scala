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
import io.fcomb.frontend.components.RepositoriesComponent
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl

object UserRepositoriesComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], slug: Option[String])

  private val component = ReactComponentB[Props]("UserRepositories").render_P { props =>
    props.slug match {
      case Some(slug) => RepositoriesComponent.apply(props.ctl, RepositoriesComponent.User(slug))
      case None       => RepositoriesComponent.apply(props.ctl, RepositoriesComponent.UserSelf)
    }
  }.build

  def apply(ctl: RouterCtl[DashboardRoute], slug: Option[String]) =
    component.apply(Props(ctl, slug))
}