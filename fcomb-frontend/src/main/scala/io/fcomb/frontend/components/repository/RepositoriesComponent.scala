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
import cats.syntax.eq._
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.frontend.components.{TimeAgoComponent, ToolbarPaginationComponent, LayoutComponent}
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.frontend.utils.PaginationUtils
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
  final case class Props(ctl: RouterCtl[DashboardRoute], namespace: Namespace)
  final case class State(repositories: Seq[RepositoryResponse], page: Int, total: Int)

  final class Backend($ : BackendScope[Props, State]) {
    private lazy val currentUser = AppCircuit.currentUser

    val limit = 25

    def updatePage(page: Int): Callback =
      $.modState(_.copy(page = page)) >> $.props.flatMap(p => getRepositories(p.namespace, page))

    def getRepositories(namespace: Namespace, page: Int) = {
      val url = namespace match {
        case Namespace.All => Resource.userSelfRepositoriesAvailable
        case Namespace.User(slug, _) =>
          if (currentUser.exists(_.username == slug)) Resource.userSelfRepositories
          else Resource.userRepositories(slug)
        case Namespace.Organization(slug, _) => Resource.organizationRepositories(slug)
      }
      val params = PaginationUtils.getParams(page, limit)
      Callback.future {
        Rpc.call[PaginationData[RepositoryResponse]](RpcMethod.GET, url, params).map {
          case Xor.Right(pd) =>
            $.modState(
              _.copy(
                repositories = pd.data,
                page = pd.getPage,
                total = pd.total
              ))
          case Xor.Left(e) => Callback.warn(e)
        }
      }
    }

    lazy val visibilityColumnStyle = js.Dictionary("width" -> "24px")
    lazy val menuColumnStyle       = js.Dictionary("width" -> "24px")

    def renderRepository(ctl: RouterCtl[DashboardRoute],
                         repository: RepositoryResponse,
                         showNamespace: Boolean) = {
      val lastModifiedAt = repository.updatedAt.getOrElse(repository.createdAt)
      val icon = repository.visibilityKind match {
        case ImageVisibilityKind.Public  => Mui.SvgIcons.ActionLockOpen
        case ImageVisibilityKind.Private => Mui.SvgIcons.ActionLock
      }
      val menuBtn = MuiIconButton()(Mui.SvgIcons.NavigationMoreVert()())
      val actions = Seq(MuiMenuItem(primaryText = "Open", key = "open")())
      val name    = if (showNamespace) repository.slug else repository.name
      MuiTableRow(key = repository.id.toString)(
        MuiTableRowColumn(style = visibilityColumnStyle, key = "visibilityKind")(
          <.span(^.title := repository.visibilityKind.toString,
                 icon(color = LayoutComponent.style.palette.primary3Color)())
        ),
        MuiTableRowColumn(key = "name")(
          ctl.link(DashboardRoute.Repository(repository.slug))(name)),
        MuiTableRowColumn(key = "lastModifiedAt")(TimeAgoComponent(lastModifiedAt)),
        MuiTableRowColumn(style = menuColumnStyle, key = "menu")(
          MuiIconMenu(iconButtonElement = menuBtn)(actions)
        )
      )
    }

    def setRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(route))

    lazy val colNames = MuiTableRow()(
      MuiTableHeaderColumn(style = visibilityColumnStyle, key = "visibilityKind")("Visibility"),
      MuiTableHeaderColumn(key = "name")("Name"),
      MuiTableHeaderColumn(key = "lastModifiedAt")("Last modified"),
      MuiTableHeaderColumn(style = menuColumnStyle, key = "menu")()
    )

    def render(props: Props, state: State) = {
      val showNamespace = props.namespace === Namespace.All
      val rows =
        if (state.repositories.isEmpty)
          Seq(
            MuiTableRow(rowNumber = 4, key = "row")(
              MuiTableRowColumn()("There are no repositories to show")))
        else state.repositories.map(renderRepository(props.ctl, _, showNamespace))

      <.section(MuiTable(selectable = false, multiSelectable = false)(
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
                  )(rows)
                ),
                ToolbarPaginationComponent(state.page, limit, state.total, updatePage _))
    }
  }

  private val component = ReactComponentB[Props]("Repositories")
    .initialState(State(Seq.empty, 1, 0))
    .renderBackend[Backend]
    .componentWillReceiveProps(cb => cb.$.backend.getRepositories(cb.nextProps.namespace, 1))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], namespace: Namespace) =
    component.apply(Props(ctl, namespace))
}
