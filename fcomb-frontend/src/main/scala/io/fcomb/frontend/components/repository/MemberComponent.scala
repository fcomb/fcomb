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
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.rpc.Formats.decodeDataResponse
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.models.OwnerKind
import io.fcomb.rpc.DataResponse
import io.fcomb.rpc.acl._
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js

object MemberComponent {
  final case class Props(repositoryName: String, ownerKind: OwnerKind)
  final case class State(members: Seq[PermissionMemberResponse], data: js.Array[String])

  final class Backend($ : BackendScope[Props, State]) {
    private def getSuggestions(q: String): Callback = {
      for {
        props <- $.props
        _ <- Callback.future {
          Rpc
            .call[DataResponse[PermissionMemberResponse]](
              RpcMethod.GET,
              Resource.repositoryPermissionsMembersSuggestions(props.repositoryName),
              Map("q" -> q.trim))
            .map {
              case Xor.Right(res) =>
                $.modState(
                  _.copy(
                    members = res.data,
                    data = mapMembers(res.data, props.ownerKind)
                  ))
              case Xor.Left(e) =>
                println(e)
                Callback.empty
            }
        }
      } yield ()
    }

    private def mapMembers(members: Seq[PermissionMemberResponse], ownerKind: OwnerKind) = {
      val xs = ownerKind match {
        case OwnerKind.User         => members.map(_.name)
        case OwnerKind.Organization => members.map(r => s"${r.title} [${r.kind}]")
      }
      js.Array(xs: _*)
    }

    private def onNewRequest(chosen: Value, idx: js.UndefOr[Int], ds: js.Array[String]): Callback =
      Callback.info(s"onNewRequest: chosen: $chosen, idx: $idx")

    private def onUpdateInput(search: SearchText, ds: js.Array[Value]): Callback =
      getSuggestions(search)

    def render(props: Props, state: State) = {
      val label = props.ownerKind match {
        case OwnerKind.User         => "Username"
        case OwnerKind.Organization => "Username or group name"
      }
      MuiAutoComplete(
        floatingLabelText = label,
        filter = MuiAutoCompleteFilters.caseInsensitiveFilter,
        dataSource = state.data,
        onNewRequest = onNewRequest _,
        onUpdateInput = onUpdateInput _
      )()
    }
  }

  private val component =
    ReactComponentB[Props]("Member")
      .initialState(State(Seq.empty, js.Array()))
      .renderBackend[Backend]
      .build

  def apply(repositoryName: String, ownerKind: OwnerKind) =
    component.apply(Props(repositoryName, ownerKind))
}
