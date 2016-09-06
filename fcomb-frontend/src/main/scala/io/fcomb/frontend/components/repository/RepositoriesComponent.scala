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
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.components.TimeAgoComponent
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.models.Formats._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.PaginationData
import io.fcomb.models.docker.distribution.ImageVisibilityKind
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object RepositoriesComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], owner: RepositoryOwner)
  final case class State(repositories: Seq[RepositoryResponse])

  final class Backend($ : BackendScope[Props, State]) {
    def getRepositories(owner: RepositoryOwner) = {
      val url = owner match {
        case RepositoryOwner.UserSelf         => Resource.userSelfRepositories
        case RepositoryOwner.User(id)         => Resource.userRepositories(id)
        case RepositoryOwner.Organization(id) => Resource.organizationRepositories(id)
      }
      Callback.future {
        Rpc.call[PaginationData[RepositoryResponse]](RpcMethod.GET, url).map {
          case Xor.Right(pd) =>
            $.modState(_.copy(pd.data))
          case Xor.Left(e) => Callback.warn(e)
        }
      }
    }

    lazy val visibilityColumnStyle = js.Dynamic.literal("width" -> "24px")

    def renderRepository(ctl: RouterCtl[DashboardRoute], repository: RepositoryResponse) = {
      val lastModifiedAt = repository.updatedAt.getOrElse(repository.createdAt)
      val icon = repository.visibilityKind match {
        case ImageVisibilityKind.Public  => Mui.SvgIcons.ActionLockOpen
        case ImageVisibilityKind.Private => Mui.SvgIcons.ActionLock
      }
      MuiTableRow(key = repository.id.toString)(
        MuiTableRowColumn(style = visibilityColumnStyle, key = "visibilityKind")(
          <.span(^.title := repository.visibilityKind.toString, icon()())
        ),
        MuiTableRowColumn(key = "name")(
          ctl.link(DashboardRoute.Repository(repository.slug))(repository.name)),
        MuiTableRowColumn(key = "lastModifiedAt")(TimeAgoComponent(lastModifiedAt))
      )
    }

    def setRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(route))

    lazy val colNames = MuiTableRow()(
      MuiTableHeaderColumn(style = visibilityColumnStyle, key = "visibilityKind")("Visible"),
      MuiTableHeaderColumn(key = "name")("Name"),
      MuiTableHeaderColumn(key = "lastModifiedAt")("Last modified")
    )

    def render(props: Props, state: State) = {
      if (state.repositories.isEmpty) <.span("No repositories. Create one!")
      else
        <.article(
          MuiTable(selectable = false, multiSelectable = false)(
            MuiTableHeader(
              adjustForCheckbox = false,
              displaySelectAll = false,
              enableSelectAll = false,
              key = "header"
            )(colNames),
            MuiTableBody(
              deselectOnClickaway = false,
              displayRowCheckbox = false,
              showRowHover = false,
              stripedRows = false,
              key = "body"
            )(state.repositories.map(renderRepository(props.ctl, _)))
          ))
    }
  }

  private val component = ReactComponentB[Props]("Repositories")
    .initialState(State(Seq.empty))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getRepositories($.props.owner))
    .componentWillReceiveProps(cb => cb.$.backend.getRepositories(cb.nextProps.owner))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], owner: RepositoryOwner) =
    component.apply(Props(ctl, owner))
}
