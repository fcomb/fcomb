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
import io.fcomb.models.{OwnerKind, PaginationData}
import io.fcomb.rpc.OrganizationResponse
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object OwnerComponent {
  final case class Props(ownerScope: RepositoryOwnerScope,
                         isAdminRoleOnly: Boolean,
                         isDisabled: Boolean,
                         cb: OwnerItem => Callback)
  final case class State(owner: Option[OwnerItem], owners: Seq[OwnerItem], data: js.Array[String])

  final class Backend($ : BackendScope[Props, State]) {
    private lazy val currentUser = AppCircuit.currentUser
      .map(u => Seq(OwnerItem(u.id, OwnerKind.User, u.username)))
      .getOrElse(Seq.empty)

    private val limit = 256

    def fetchOwners(): Callback = {
      for {
        props <- $.props
        _ <- Callback.future {
          val role = if (props.isAdminRoleOnly) "admin" else ""
          val params = Map(
            "role"  -> role,
            "limit" -> limit.toString()
          ).filter(_._2.nonEmpty)
          Rpc
            .call[PaginationData[OrganizationResponse]](RpcMethod.GET,
                                                        Resource.userSelfOrganizations,
                                                        params)
            .map {
              case Xor.Right(res) =>
                val orgs   = res.data.map(o => OwnerItem(o.id, OwnerKind.Organization, o.name))
                val owners = currentUser ++ orgs
                val data   = js.Array(owners.map(_.slug): _*)
                val owner = props.ownerScope match {
                  case RepositoryOwner.UserSelf => currentUser.headOption
                  case RepositoryOwner.Organization(slug)                    =>
                    owners.collectFirst { case res @ OwnerItem(_, _, `slug`) => res }
                }
                $.modState(
                  _.copy(
                    owner = owner,
                    owners = owners,
                    data = data
                  )) >> owner.map(props.cb(_)).getOrElse(Callback.empty)
              case Xor.Left(e) => Callback.warn(e)
            }
        }
      } yield ()
    }

    private def onChange(e: ReactEventI, idx: Int, owner: OwnerItem): Callback = {
      (for {
        owners <- $.state.map(_.owners)
        cb     <- $.props.map(_.cb)
      } yield (owners, cb)).flatMap {
        case (owners, cb) =>
          $.modState(_.copy(owner = Some(owner))) >> cb(owner)
      }
    }

    def render(props: Props, state: State) = {
      val owners = state.owners.map(o =>
        MuiMenuItem[OwnerItem](key = o.slug, value = o, primaryText = o.slug)())
      MuiSelectField[OwnerItem](
        floatingLabelText = "Owner",
        disabled = props.isDisabled,
        value = state.owner.orUndefined,
        maxHeight = limit + 1,
        onChange = onChange _
      )(owners)
    }
  }

  private val component =
    ReactComponentB[Props]("Owner")
      .initialState(State(None, Seq.empty, js.Array()))
      .renderBackend[Backend]
      .componentWillMount(_.backend.fetchOwners())
      .build

  def apply(ownerScope: RepositoryOwnerScope,
            isAdminRoleOnly: Boolean,
            isDisabled: Boolean,
            cb: OwnerItem => Callback) =
    component.apply(Props(ownerScope, isAdminRoleOnly, isDisabled, cb))
}
