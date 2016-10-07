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
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.{LayoutComponent, TimeAgoComponent, ToolbarPaginationComponent}
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
    val limit = 25

    def updatePage(page: Int): Callback =
      $.modState(_.copy(page = page)) >> $.props.flatMap(p => getRepositories(p.namespace, page))

    def getRepositories(namespace: Namespace, page: Int) =
      Callback.future {
        Rpc.getNamespaceRepositories(namespace, page, limit).map {
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

    lazy val visibilityColumnStyle = js.Dictionary("width" -> "64px")
    lazy val menuColumnStyle       = js.Dictionary("width" -> "48px", "padding" -> "0px")

    def setRepositoryRoute(slug: String)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(DashboardRoute.Repository(slug)))

    def renderRepository(ctl: RouterCtl[DashboardRoute],
                         repository: RepositoryResponse,
                         showNamespace: Boolean) = {
      val lastModifiedAt = repository.updatedAt.getOrElse(repository.createdAt)
      val icon = repository.visibilityKind match {
        case ImageVisibilityKind.Public  => Mui.SvgIcons.ActionLockOpen
        case ImageVisibilityKind.Private => Mui.SvgIcons.ActionLock
      }
      val menuBtn =
        MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
      val actions = Seq(
        MuiMenuItem(primaryText = "Open",
                    key = "open",
                    onTouchTap = setRepositoryRoute(repository.slug) _)())
      val name   = if (showNamespace) repository.slug else repository.name
      val target = DashboardRoute.Repository(repository.slug)
      MuiTableRow(key = repository.id.toString)(
        MuiTableRowColumn(style = visibilityColumnStyle, key = "visibilityKind")(
          <.span(^.title := repository.visibilityKind.toString,
                 icon(color = LayoutComponent.style.palette.primary3Color)())
        ),
        MuiTableRowColumn(key = "name")(
          <.a(LayoutComponent.linkAsTextStyle,
              ^.href := ctl.urlFor(target).value,
              ctl.setOnLinkClick(target))(name)),
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
    .componentWillReceiveProps(lc => lc.$.backend.getRepositories(lc.nextProps.namespace, 1))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], namespace: Namespace) =
    component.apply(Props(ctl, namespace))
}
