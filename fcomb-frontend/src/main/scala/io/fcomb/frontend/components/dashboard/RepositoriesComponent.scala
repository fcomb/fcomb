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

import cats.data.Xor
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.PaginationData
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object RepositoriesComponent {
  final case class State(repositories: Seq[RepositoryResponse])

  final case class Backend($ : BackendScope[RouterCtl[DashboardRoute], State]) {
    def getRepositories() = {
      Callback.future {
        Rpc
          .call[PaginationData[RepositoryResponse]](RpcMethod.GET, Resource.userRepositories)
          .map {
            case Xor.Right(pd) =>
              $.modState(_.copy(pd.data))
            case Xor.Left(e) =>
              println(e)
              Callback.empty
          }
      }
    }

    def renderRepository(ctl: RouterCtl[DashboardRoute], repository: RepositoryResponse) = {
      <.li(ctl.link(DashboardRoute.Repository(repository.slug))(repository.name))
    }

    def renderRepositories(ctl: RouterCtl[DashboardRoute], repositories: Seq[RepositoryResponse]) = {
      if (repositories.isEmpty) <.span("No repositories. Create one!")
      else <.ul(repositories.map(renderRepository(ctl, _)))
    }

    def render(ctl: RouterCtl[DashboardRoute], state: State) = {
      <.div(<.h2("Repositories"), renderRepositories(ctl, state.repositories))
    }
  }

  private val component = ReactComponentB[RouterCtl[DashboardRoute]]("RepositoriesComponent")
    .initialState(State(Seq.empty))
    .renderBackend[Backend]
    .componentWillMount(_.backend.getRepositories())
    .build

  def apply(ctl: RouterCtl[DashboardRoute]) = component.apply(ctl)
}
