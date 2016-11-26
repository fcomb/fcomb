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

import chandu0101.scalajs.react.components.Implicits._
import chandu0101.scalajs.react.components.materialui._
import io.fcomb.frontend.api.Rpc
import japgolly.scalajs.react._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object NamespaceComponent {
  final case class Props(namespace: Namespace,
                         canCreateRoleOnly: Boolean,
                         isAllNamespace: Boolean,
                         isDisabled: Boolean,
                         isFullWidth: Boolean,
                         cb: Namespace => Callback)
  final case class State(namespace: Option[Namespace],
                         namespaces: Seq[OwnerNamespace],
                         data: js.Array[String])

  final class Backend($ : BackendScope[Props, State]) {
    private val limit = 256

    def fetchNamespaces(): Callback =
      $.props.flatMap { props =>
        Callback.future(Rpc.getUserSelfOrganizations(props.canCreateRoleOnly, limit).map {
          case Right(res) =>
            val orgs       = res.data.map(o => Namespace.Organization(o.name, Some(o.id)))
            val namespaces = Namespace.currentUser ++ orgs
            val data       = js.Array(namespaces.map(_.slug): _*)
            val ns = props.namespace match {
              case on: OwnerNamespace =>
                namespaces.collectFirst { case o: OwnerNamespace if o.slug == on.slug => o }
              case _ => None
            }
            val namespace = ns.orElse(Namespace.currentUser.headOption)
            $.modState(
              _.copy(
                namespace = namespace,
                namespaces = namespaces,
                data = data
              )) >> namespaceCallback(namespace)
          case Left(e) => Callback.warn(e)
        })
      }

    private def namespaceCallback(namespace: Namespace): Callback =
      $.props.flatMap(_.cb(namespace))

    private def namespaceCallback(namespace: Option[Namespace]): Callback =
      namespace.fold(Callback.empty)(namespaceCallback)

    private def onChange(e: ReactEventI, idx: Int, namespace: Namespace): Callback =
      $.state.flatMap {
        case state if !state.namespace.contains(namespace) =>
          $.modState(_.copy(namespace = Some(namespace))) >> namespaceCallback(namespace)
        case _ => Callback.empty
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
        fullWidth = props.isFullWidth,
        disabled = props.isDisabled,
        value = state.namespace.orUndefined,
        maxHeight = limit + prefixCount,
        onChange = onChange _,
        id = "namespace"
      )(namespaceItems)
    }
  }

  private val component =
    ReactComponentB[Props]("Namespace")
      .initialState(State(None, Seq.empty, js.Array()))
      .renderBackend[Backend]
      .componentDidMount(_.backend.fetchNamespaces())
      .build

  def apply(namespace: Namespace,
            canCreateRoleOnly: Boolean,
            isAllNamespace: Boolean,
            isDisabled: Boolean,
            isFullWidth: Boolean,
            cb: Namespace => Callback) =
    component.apply(
      Props(namespace, canCreateRoleOnly, isAllNamespace, isDisabled, isFullWidth, cb))
}
