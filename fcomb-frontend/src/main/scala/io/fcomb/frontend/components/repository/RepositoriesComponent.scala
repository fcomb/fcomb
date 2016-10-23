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
import diode.data.{Empty, Failed, Pot, Ready}
import diode.react.ReactPot._
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.{
  LayoutComponent,
  PaginationOrderState,
  Table,
  TimeAgoComponent,
  ToolbarPaginationComponent
}
import io.fcomb.frontend.styles.App
import io.fcomb.models.errors.ErrorsException
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scalacss.ScalaCssReact._

object RepositoriesComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], namespace: Namespace)
  final case class State(repositories: Pot[Seq[RepositoryResponse]],
                         pagination: PaginationOrderState) {
    def flipSortColumn(column: String): State = {
      val sortOrder =
        if (pagination.sortColumn == column) pagination.sortOrder.flip
        else pagination.sortOrder
      this.copy(pagination = pagination.copy(sortColumn = column, sortOrder = sortOrder))
    }
  }

  private def defaultPagination = PaginationOrderState("slug")

  final class Backend($ : BackendScope[Props, State]) {
    val limit = 25

    def updatePage(page: Int): Callback =
      $.state.zip($.props).flatMap {
        case (state, props) =>
          val pagination = state.pagination.copy(page = page)
          $.setState(state.copy(pagination = pagination)) >> getRepos(props.namespace, pagination)
      }

    def getRepos(namespace: Namespace, pos: PaginationOrderState) =
      $.state.flatMap { state =>
        if (state.repositories.isPending) Callback.empty
        else
          $.setState(state.copy(repositories = state.repositories.pending())) >>
            Callback.future(Rpc
              .getNamespaceRepositories(namespace, pos.sortColumn, pos.sortOrder, pos.page, limit)
              .map {
                case Xor.Right(pd) =>
                  $.modState(st =>
                    st.copy(repositories = Ready(pd.data),
                            pagination = st.pagination.copy(total = pd.total)))
                case Xor.Left(errs) =>
                  $.modState(_.copy(repositories = Failed(ErrorsException(errs))))
              })
      }

    def setRepositoryRoute(route: DashboardRoute)(e: ReactEventH): Callback =
      $.props.flatMap(_.ctl.set(route))

    def renderRepository(ctl: RouterCtl[DashboardRoute],
                         repository: RepositoryResponse,
                         showNamespace: Boolean) = {
      val target         = DashboardRoute.Repository(repository.slug)
      val lastModifiedAt = repository.updatedAt.getOrElse(repository.createdAt)
      val menuBtn =
        MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
      val actions = Seq(
        MuiMenuItem(primaryText = "Open",
                    key = "open",
                    onTouchTap = setRepositoryRoute(target) _)(),
        MuiMenuItem(primaryText = "Edit",
                    key = "edit",
                    onTouchTap =
                      setRepositoryRoute(DashboardRoute.EditRepository(repository.slug)) _)()
      )
      val name = if (showNamespace) repository.slug else repository.name
      MuiTableRow(key = repository.id.toString)(
        MuiTableRowColumn(style = App.visibilityColumnStyle, key = "visibilityKind")(
          RepositoryComponent.visiblityIcon(repository.visibilityKind)),
        MuiTableRowColumn(key = "name")(
          <.a(LayoutComponent.linkAsTextStyle,
              ^.href := ctl.urlFor(target).value,
              ctl.setOnLinkClick(target))(name)),
        MuiTableRowColumn(key = "lastModifiedAt")(TimeAgoComponent(lastModifiedAt)),
        MuiTableRowColumn(style = App.menuColumnStyle, key = "menu")(
          MuiIconMenu(iconButtonElement = menuBtn)(actions)))
    }

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _         <- e.preventDefaultCB
        state     <- $.state.map(_.flipSortColumn(column))
        _         <- $.setState(state)
        namespace <- $.props.map(_.namespace)
        _         <- getRepos(namespace, state.pagination)
      } yield ()

    def renderRepositories(props: Props,
                           repositories: Seq[RepositoryResponse],
                           p: PaginationOrderState): ReactElement =
      if (repositories.isEmpty) <.div(App.infoMsg, "There are no repositories to show yet")
      else {
        val showNamespace = props.namespace === Namespace.All
        val columns =
          MuiTableRow()(Table.renderHeader("Visibility",
                                           "visibilityKind",
                                           p,
                                           updateSort _,
                                           style = App.visibilityColumnStyle),
                        Table.renderHeader("Name", "slug", p, updateSort _),
                        Table.renderHeader("Last modified", "updatedAt", p, updateSort _),
                        MuiTableHeaderColumn(style = App.menuColumnStyle, key = "menu")())
        val rows = repositories.map(renderRepository(props.ctl, _, showNamespace))

        <.div(MuiTable(selectable = false, multiSelectable = false)(
                MuiTableHeader(
                  adjustForCheckbox = false,
                  displaySelectAll = false,
                  enableSelectAll = false,
                  key = "header"
                )(columns),
                MuiTableBody(
                  deselectOnClickaway = false,
                  displayRowCheckbox = false,
                  showRowHover = false,
                  stripedRows = false,
                  key = "body"
                )(rows)
              ),
              ToolbarPaginationComponent(p.page, limit, p.total, updatePage _))
      }

    def render(props: Props, state: State): ReactElement =
      <.section(state.repositories.render(rs => renderRepositories(props, rs, state.pagination)))
  }

  private val component = ReactComponentB[Props]("Repositories")
    .initialState(State(Empty, defaultPagination))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getRepos($.props.namespace, defaultPagination))
    .componentWillReceiveProps(lc =>
      lc.$.backend.getRepos(lc.nextProps.namespace, defaultPagination))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], namespace: Namespace) =
    component.apply(Props(ctl, namespace))
}
