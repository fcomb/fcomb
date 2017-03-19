/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Implicits._
import io.fcomb.models.OwnerKind
import io.fcomb.rpc.acl._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object MemberComponent {
  final case class Props(slug: String,
                         ownerKind: OwnerKind,
                         searchText: Option[String],
                         isDisabled: Boolean,
                         errorText: Option[String],
                         isFullWidth: Boolean,
                         cb: PermissionMemberResponse => Callback)
  final case class State(member: Option[PermissionMemberResponse],
                         members: Seq[PermissionMemberResponse],
                         data: js.Array[String])

  final class Backend($ : BackendScope[Props, State]) {
    private def getSuggestions(q: String): Callback =
      for {
        props <- $.props
        _ <- Callback.future(Rpc.getRepositoryPermissionsMembers(props.slug, q.trim).map {
          case Right(res) =>
            val data = js.Array(res.data.map(_.title): _*)
            $.modState(
              _.copy(
                members = res.data,
                data = data
              ))
          case Left(e) => Callback.warn(e)
        })
      } yield ()

    private def onNewRequest(value: String, index: Int): Callback =
      (for {
        members <- $.state.map(_.members)
        cb      <- $.props.map(_.cb)
      } yield (members, cb)).flatMap {
        case (members, cb) =>
          members.lift(index) match {
            case Some(member) if value == member.title =>
              $.modState(_.copy(member = Some(member))) >> cb(member)
            case _ => Callback.warn(s"Unknown member: $value")
          }
      }

    private def onUpdateInput(search: String, ds: js.Array[String], obj: js.Object): Callback =
      getSuggestions(search)

    def render(props: Props, state: State) = {
      val label = props.ownerKind match {
        case OwnerKind.User         => "Username"
        case OwnerKind.Organization => "Username or group name"
      }
      MuiAutoComplete(
        id = "member",
        floatingLabelText = label,
        filter = js.defined(MuiAutoCompleteFilters.noFilter),
        fullWidth = props.isFullWidth,
        disabled = props.isDisabled,
        dataSource = state.data,
        searchText = props.searchText.orUndefined,
        errorText = props.errorText,
        openOnFocus = true,
        onNewRequest = onNewRequest _,
        onUpdateInput = onUpdateInput _
      )()
    }
  }

  private val component =
    ReactComponentB[Props]("Member")
      .initialState(State(None, Seq.empty, js.Array()))
      .renderBackend[Backend]
      .build

  def apply(slug: String,
            ownerKind: OwnerKind,
            searchText: Option[String],
            isDisabled: Boolean,
            errorText: Option[String],
            isFullWidth: Boolean,
            cb: PermissionMemberResponse => Callback) =
    component.apply(Props(slug, ownerKind, searchText, isDisabled, errorText, isFullWidth, cb))
}
