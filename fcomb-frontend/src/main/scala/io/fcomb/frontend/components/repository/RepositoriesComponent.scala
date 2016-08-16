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
  final case class Props(ctl: RouterCtl[DashboardRoute], ownerScope: OwnerScope)
  final case class State(repositories: Seq[RepositoryResponse])

  class Backend($ : BackendScope[Props, State]) {
    val urlCB = $.props.map(_.ownerScope).map {
      case OwnerScope.UserSelf         => Resource.userSelfRepositories
      case Owner.User(id)              => Resource.userRepositories(id)
      case OwnerScope.Organization(id) => Resource.organizationRepositories(id)
    }

    def getRepositories() = {
      for {
        url <- urlCB
        _ <- Callback.future {
              Rpc.call[PaginationData[RepositoryResponse]](RpcMethod.GET, url).map {
                case Xor.Right(pd) =>
                  $.modState(_.copy(pd.data))
                case Xor.Left(e) =>
                  println(e)
                  Callback.empty
              }
            }
      } yield ()
    }

    def renderRepository(ctl: RouterCtl[DashboardRoute], repository: RepositoryResponse) = {
      <.li(ctl.link(DashboardRoute.Repository(repository.slug))(repository.slug))
    }

    def renderRepositories(ctl: RouterCtl[DashboardRoute], repositories: Seq[RepositoryResponse]) = {
      if (repositories.isEmpty) <.span("No repositories. Create one!")
      else <.ul(repositories.map(renderRepository(ctl, _)))
    }

    def render(props: Props, state: State) = {
      <.div(<.h2("Repositories"), renderRepositories(props.ctl, state.repositories))
    }
  }

  private val component = ReactComponentB[Props]("Repositories")
    .initialState(State(Seq.empty))
    .renderBackend[Backend]
    .componentWillMount(_.backend.getRepositories())
    .build

  def apply(ctl: RouterCtl[DashboardRoute], ownerScope: OwnerScope) =
    component.apply(Props(ctl, ownerScope))
}
