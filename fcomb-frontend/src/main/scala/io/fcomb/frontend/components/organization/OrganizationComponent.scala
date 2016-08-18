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

package io.fcomb.frontend.components.organization

import cats.data.Xor
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.frontend.components.repository.{RepositoriesComponent, RepositoryOwner}
import io.fcomb.json.rpc.Formats._
import io.fcomb.rpc.OrganizationResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object OrganizationComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], name: String)
  final case class FormState(name: String, isFormDisabled: Boolean)
  final case class State(org: Option[OrganizationResponse], form: Option[FormState])

  class Backend($ : BackendScope[Props, State]) {
    def getOrg(name: String): Callback = {
      Callback.future {
        Rpc.call[OrganizationResponse](RpcMethod.GET, Resource.organization(name)).map {
          case Xor.Right(org) =>
            $.modState(_.copy(org = Some(org)))
          case Xor.Left(e) =>
            println(e)
            Callback.empty
        }
      }
    }

    def render(props: Props, state: State) = {
      <.section(
        <.h2(s"Organization ${props.name}"),
        <.ul(
          <.li(props.ctl.link(DashboardRoute.OrganizationGroups(props.name))("Groups")),
          <.li(
            props.ctl.link(DashboardRoute.NewOrganizationRepository(props.name))("New repository"))
        ),
        RepositoriesComponent.apply(props.ctl, RepositoryOwner.Organization(props.name))
      )
    }
  }

  private val component = ReactComponentB[Props]("Organization")
    .initialState(State(None, None))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getOrg($.props.name))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], name: String) =
    component(Props(ctl, name))
}
