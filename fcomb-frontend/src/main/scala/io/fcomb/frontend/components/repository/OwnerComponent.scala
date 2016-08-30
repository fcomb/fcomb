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
import io.fcomb.frontend.dispatcher.AppCircuit
import io.fcomb.json.rpc.Formats.decodeOrganizationResponse
import io.fcomb.json.models.Formats.decodePaginationData
import io.fcomb.models.{Owner, OwnerKind, PaginationData}
import io.fcomb.rpc.OrganizationResponse
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object OwnerComponent {
  final case class Props(ownerScope: RepositoryOwnerScope,
                         isDisabled: Boolean,
                         cb: Owner => Callback)
  final case class OwnerItem(id: Int, kind: OwnerKind, title: String)
  final case class State(owner: Option[OwnerItem],
                         owners: Seq[OwnerItem],
                         searchText: Option[String],
                         data: js.Array[String])

  final class Backend($ : BackendScope[Props, State]) {
    private lazy val currentUser = AppCircuit.currentUser.map { u =>
      Seq(OwnerItem(u.id, OwnerKind.User, u.username))
    }.getOrElse(Seq.empty)

    private def getSuggestions(q: String): Callback = {
      for {
        props <- $.props
        _ <- Callback.future {
          val params = Map(
            "name"  -> q.trim,
            "role"  -> "admin",
            "limit" -> "256"
          )
          Rpc
            .call[PaginationData[OrganizationResponse]](RpcMethod.GET,
                                                        Resource.userSelfOrganizations,
                                                        params)
            .map {
              case Xor.Right(res) =>
                val owners = currentUser ++ res.data.map { o =>
                  OwnerItem(o.id, OwnerKind.Organization, o.name)
                }
                val data = js.Array(owners.map(_.title): _*)
                $.modState(
                  _.copy(
                    owners = owners,
                    data = data
                  ))
              case Xor.Left(e) => Callback.warn(e)
            }
        }
      } yield ()
    }

    private def setDefaultOwner(): Callback = {
      (for {
        ownerScope <- $.props.map(_.ownerScope)
        owners     <- $.state.map(_.owners)
      } yield (ownerScope, owners)).flatMap {
        case (ownerScope, owners) =>
          val owner = ownerScope match {
            case RepositoryOwner.UserSelf => currentUser.headOption
            case RepositoryOwner.Organization(slug) =>
              owners.collectFirst {
                case res @ OwnerItem(_, _, `slug`) => res
              }
          }
          $.modState(
            _.copy(
              owner = owner,
              searchText = owner.map(_.title)
            ))
      }
    }

    def fetchDefaultOwners(): Callback =
      getSuggestions("") >> setDefaultOwner()

    private def onNewRequest(chosen: Value, idx: js.UndefOr[Int], ds: js.Array[String]): Callback = {
      idx.toOption match {
        case Some(index) =>
          (for {
            owners <- $.state.map(_.owners)
            cb     <- $.props.map(_.cb)
          } yield (owners, cb)).flatMap {
            case (owners, cb) =>
              owners.lift(index) match {
                case Some(owner) if chosen == owner.title =>
                  $.modState(_.copy(owner = Some(owner))) >> cb(Owner(owner.id, owner.kind))
                case _ => Callback.warn(s"Unknown owner: $chosen")
              }
          }
        case _ => Callback.warn("Empty index")
      }
    }

    private def onUpdateInput(search: SearchText, ds: js.Array[Value]): Callback =
      getSuggestions(search)

    def render(props: Props, state: State) = {
      MuiAutoComplete(
        floatingLabelText = "Owner",
        filter = MuiAutoCompleteFilters.caseInsensitiveFilter,
        disabled = props.isDisabled,
        dataSource = state.data,
        searchText = state.searchText.orUndefined,
        openOnFocus = true,
        onNewRequest = onNewRequest _,
        onUpdateInput = onUpdateInput _
      )()
    }
  }

  private val component =
    ReactComponentB[Props]("Owner")
      .initialState(State(None, Seq.empty, None, js.Array()))
      .renderBackend[Backend]
      .componentWillMount(_.backend.fetchDefaultOwners())
      .build

  def apply(ownerScope: RepositoryOwnerScope, isDisabled: Boolean, cb: Owner => Callback) =
    component.apply(Props(ownerScope, isDisabled, cb))
}
