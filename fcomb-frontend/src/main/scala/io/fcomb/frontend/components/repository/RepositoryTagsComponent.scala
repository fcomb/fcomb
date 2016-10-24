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
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import diode.data.{Empty, Failed, Pot, Ready}
import diode.react.ReactPot._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.{
  CopyToClipboardComponent,
  PaginationOrderState,
  SizeInBytesComponent,
  Table,
  TimeAgoComponent
}
import io.fcomb.frontend.styles.App
import io.fcomb.models.errors.ErrorsException
import io.fcomb.frontend.utils.RepositoryUtils
import io.fcomb.models.SortOrder
import io.fcomb.rpc.docker.distribution.RepositoryTagResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scalacss.ScalaCssReact._

object RepositoryTagsComponent {
  final case class Props(slug: String)
  final case class State(tags: Pot[Seq[RepositoryTagResponse]], pagination: PaginationOrderState) {
    def flipSortColumn(column: String): State = {
      val sortOrder =
        if (pagination.sortColumn == column) pagination.sortOrder.flip
        else pagination.sortOrder
      this.copy(pagination = pagination.copy(sortColumn = column, sortOrder = sortOrder))
    }
  }

  final class Backend($ : BackendScope[Props, State]) {
    val digestLength = 12
    val limit        = 25

    def getTags(pos: PaginationOrderState): Callback =
      $.props.flatMap { props =>
        Callback.future(
          Rpc.getRepositoryTags(props.slug, pos.sortColumn, pos.sortOrder, pos.page, limit).map {
            case Xor.Right(pd) =>
              $.modState(st =>
                st.copy(tags = Ready(pd.data), pagination = st.pagination.copy(total = pd.total)))
            case Xor.Left(errs) => $.modState(_.copy(tags = Failed(ErrorsException(errs))))
          })
      }

    def renderTagRow(props: Props, tag: RepositoryTagResponse) = {
      val menuBtn =
        MuiIconButton()(Mui.SvgIcons.NavigationMoreVert(color = Mui.Styles.colors.lightBlack)())
      val dockerPullCommand = RepositoryUtils.dockerPullCommand(props.slug, tag.tag)
      val actions = Seq(
        CopyToClipboardComponent(
          dockerPullCommand,
          js.undefined,
          MuiMenuItem(primaryText = "Copy docker pull command", key = "copy")(),
          key = "copyDockerPull"),
        CopyToClipboardComponent(tag.digest,
                                 js.undefined,
                                 MuiMenuItem(primaryText = "Copy image digest", key = "copy")(),
                                 key = "copyDigest"))
      MuiTableRow(key = tag.tag)(
        MuiTableRowColumn(key = "tag")(tag.tag),
        MuiTableRowColumn(key = "updatedAt")(TimeAgoComponent(tag.updatedAt)),
        MuiTableRowColumn(key = "length")(SizeInBytesComponent(tag.length)),
        MuiTableRowColumn(key = "digest")(
          <.div(App.digest, ^.title := tag.digest, tag.digest.take(digestLength))),
        MuiTableRowColumn(style = App.menuColumnStyle, key = "actions")(
          MuiIconMenu(iconButtonElement = menuBtn)(actions)))
    }

    def updateSort(column: String)(e: ReactEventH): Callback =
      for {
        _     <- e.preventDefaultCB
        state <- $.state.map(_.flipSortColumn(column))
        _     <- $.setState(state)
        _     <- getTags(state.pagination)
      } yield ()

    def updatePage(page: Int): Callback =
      $.state.flatMap { state =>
        val pagination = state.pagination.copy(page = page)
        $.setState(state.copy(pagination = pagination)) >> getTags(pagination)
      }

    def renderTags(props: Props, tags: Seq[RepositoryTagResponse], p: PaginationOrderState) =
      if (tags.isEmpty) <.div(App.infoMsg, "There are no tags to show yet")
      else {
        val columns = MuiTableRow()(
          Table.header("Tag", "tag", p, updateSort _),
          Table.header("Last modified", "updatedAt", p, updateSort _),
          Table.header("Size", "length", p, updateSort _),
          Table.header("Image", "digest", p, updateSort _),
          MuiTableHeaderColumn(style = App.menuColumnStyle, key = "actions")())
        val rows = tags.map(renderTagRow(props, _))
        Table(columns, rows, p.page, limit, p.total, updatePage _)
      }

    def render(props: Props, state: State): ReactElement =
      <.section(state.tags.render(ts => renderTags(props, ts, state.pagination)))
  }

  private val component = ReactComponentB[Props]("RepositoryTags")
    .initialState(State(Empty, PaginationOrderState("updatedAt", SortOrder.Desc)))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getTags($.state.pagination))
    .build

  def apply(slug: String) =
    component.apply(Props(slug))
}
