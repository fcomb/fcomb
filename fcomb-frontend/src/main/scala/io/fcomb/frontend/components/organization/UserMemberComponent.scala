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
import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.components.Implicits._
import io.fcomb.rpc.UserResponse
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object UserMemberComponent {
  final case class Props(slug: String,
                         group: String,
                         searchText: Option[String],
                         isDisabled: Boolean,
                         errorText: Option[String],
                         isFullWidth: Boolean,
                         cb: UserResponse => Callback)
  final case class State(member: Option[UserResponse],
                         members: Seq[UserResponse],
                         data: js.Array[String])

  final class Backend($ : BackendScope[Props, State]) {
    private def getSuggestions(q: String): Callback =
      for {
        props <- $.props
        _ <- Callback.future(Rpc.getOrganizationGroupMembers(props.slug, props.group, q.trim).map {
          case Xor.Right(res) =>
            val data = js.Array(res.data.map(_.title): _*)
            $.modState(_.copy(members = res.data, data = data))
          case Xor.Left(e) => Callback.warn(e)
        })
      } yield ()

    private def onNewRequest(chosen: Value, idx: js.UndefOr[Int], ds: js.Array[String]): Callback =
      idx.toOption match {
        case Some(index) =>
          (for {
            members <- $.state.map(_.members)
            cb      <- $.props.map(_.cb)
          } yield (members, cb)).flatMap {
            case (members, cb) =>
              members.lift(index) match {
                case Some(member) if chosen == member.title =>
                  $.modState(_.copy(member = Some(member))) >> cb(member)
                case _ => Callback.warn(s"Unknown member: $chosen")
              }
          }
        case _ => Callback.warn("Empty index")
      }

    private def onUpdateInput(search: SearchText, ds: js.Array[Value]): Callback =
      getSuggestions(search)

    def render(props: Props, state: State) =
      MuiAutoComplete(
        id = "member",
        floatingLabelText = "Username",
        filter = MuiAutoCompleteFilters.noFilter,
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

  private val component =
    ReactComponentB[Props]("UserMember")
      .initialState(State(None, Seq.empty, js.Array()))
      .renderBackend[Backend]
      .build

  def apply(slug: String,
            group: String,
            searchText: Option[String],
            isDisabled: Boolean,
            errorText: Option[String],
            isFullWidth: Boolean,
            cb: UserResponse => Callback) =
    component.apply(Props(slug, group, searchText, isDisabled, errorText, isFullWidth, cb))
}
