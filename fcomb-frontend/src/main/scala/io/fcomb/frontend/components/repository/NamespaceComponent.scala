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
import io.fcomb.models.PaginationData
import io.fcomb.rpc.OrganizationResponse
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object NamespaceComponent {
  final case class Props(namespace: Namespace,
                         isAdminRoleOnly: Boolean,
                         isAllNamespace: Boolean,
                         isDisabled: Boolean,
                         cb: Namespace => Callback,
                         fullWidth: Boolean)
  final case class State(namespace: Option[Namespace],
                         namespaces: Seq[OwnerNamespace],
                         data: js.Array[String])

  final class Backend($ : BackendScope[Props, State]) {
    private lazy val currentUser =
      AppCircuit.currentUser
        .map(u => Seq(Namespace.User(u.username, Some(u.id))))
        .getOrElse(Seq.empty)

    private val limit = 256

    def fetchNamespaces(): Callback = {
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
                val orgs       = res.data.map(o => Namespace.Organization(o.name, Some(o.id)))
                val namespaces = currentUser ++ orgs
                val data       = js.Array(namespaces.map(_.slug): _*)
                val namespace = (props.namespace match {
                  case on: OwnerNamespace                                                 =>
                    namespaces.collectFirst { case o: OwnerNamespace if o.slug == on.slug => o }
                  case _                                                                  => None
                }).orElse(currentUser.headOption)
                $.modState(
                  _.copy(
                    namespace = namespace,
                    namespaces = namespaces,
                    data = data
                  )) >> namespace.map(props.cb(_)).getOrElse(Callback.empty)
              case Xor.Left(e) => Callback.warn(e)
            }
        }
      } yield ()
    }

    private def onChange(e: ReactEventI, idx: Int, namespace: Namespace): Callback = {
      for {
        props <- $.props
        _     <- $.modState(_.copy(namespace = Some(namespace)))
        _     <- props.cb(namespace)
      } yield ()
    }

    lazy val allMenuItem = Seq(
      MuiMenuItem[Namespace](key = "all", value = Namespace.All, primaryText = "All")(),
      MuiDivider(key = "divider")())

    def render(props: Props, state: State) = {
      val (prefix, prefixCount) =
        if (props.isAllNamespace) (allMenuItem, 2) // current user and all namespaces
        else (Nil, 1) // only current user
      val namespaceItems = prefix ++ state.namespaces.groupBy(_.groupTitle).flatMap {
        case (groupTitle, xs) =>
          MuiSubheader(key = groupTitle)(groupTitle) +:
            xs.sortBy(_.slug)
              .map(o => MuiMenuItem[Namespace](key = o.slug, value = o, primaryText = o.slug)())
      }
      MuiSelectField[Namespace](
        floatingLabelText = "User or organization",
        fullWidth = props.fullWidth,
        disabled = props.isDisabled,
        value = state.namespace.orUndefined,
        maxHeight = limit + prefixCount,
        onChange = onChange _
      )(namespaceItems)
    }
  }

  private val component =
    ReactComponentB[Props]("Namespace")
      .initialState(State(None, Seq.empty, js.Array()))
      .renderBackend[Backend]
      .componentWillMount(_.backend.fetchNamespaces())
      .build

  def apply(namespace: Namespace,
            isAdminRoleOnly: Boolean,
            isAllNamespace: Boolean,
            isDisabled: Boolean,
            cb: Namespace => Callback,
            fullWidth: Boolean = false) =
    component.apply(Props(namespace, isAdminRoleOnly, isAllNamespace, isDisabled, cb, fullWidth))
}
