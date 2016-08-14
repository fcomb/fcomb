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
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.PaginationData
import io.fcomb.rpc.OrganizationResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object OrganizationsComponent {
  final case class State(orgs: Seq[OrganizationResponse])

  class Backend($ : BackendScope[RouterCtl[DashboardRoute], State]) {
    def getOrgs() = {
      Callback.future {
        Rpc
          .call[PaginationData[OrganizationResponse]](RpcMethod.GET,
                                                      Resource.userSelfOrganizations)
          .map {
            case Xor.Right(pd) =>
              $.modState(_.copy(pd.data))
            case Xor.Left(e) =>
              println(e)
              Callback.empty
          }
      }
    }

    def renderOrg(ctl: RouterCtl[DashboardRoute], org: OrganizationResponse) = {
      <.li(ctl.link(DashboardRoute.Organization(org.name))(org.name))
    }

    def renderOrgs(ctl: RouterCtl[DashboardRoute], orgs: Seq[OrganizationResponse]) = {
      if (orgs.isEmpty) <.span("No organizations. Create one!")
      else <.ul(orgs.map(renderOrg(ctl, _)))
    }

    def render(ctl: RouterCtl[DashboardRoute], state: State) = {
      <.div(<.h2("Organizations"), renderOrgs(ctl, state.orgs))
    }
  }

  private val component = ReactComponentB[RouterCtl[DashboardRoute]]("Organizations")
    .initialState(State(Seq.empty))
    .renderBackend[Backend]
    .componentWillMount(_.backend.getOrgs())
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) = component.apply(ctl)
}
