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
import io.fcomb.frontend.DashboardRoute
import io.fcomb.frontend.api.{Rpc, RpcMethod, Resource}
import io.fcomb.json.rpc.docker.distribution.Formats.decodeRepositoryResponse
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import japgolly.scalajs.react._
import japgolly.scalajs.react.extra.router.RouterCtl
import japgolly.scalajs.react.vdom.prefix_<^._
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

object RepositorySettingsComponent {
  final case class Props(ctl: RouterCtl[DashboardRoute], repositoryName: String)
  final case class State(repository: Option[RepositoryResponse])

  class Backend($ : BackendScope[Props, State]) {
    def getRepository(name: String): Callback = {
      Callback.future {
        Rpc.call[RepositoryResponse](RpcMethod.GET, Resource.repository(name)).map {
          case Xor.Right(repository) =>
            $.modState(_.copy(repository = Some(repository)))
          case Xor.Left(e) => Callback.warn(e)
        }
      }
    }

    def render(props: Props, state: State): ReactElement = {
      val components: Seq[TagMod] = state.repository match {
        case Some(repository) =>
          Seq(PermissionsComponent.apply(props.ctl, props.repositoryName, repository.owner.kind),
              RepositoryVisibilityComponent
                .apply(props.ctl, props.repositoryName, repository.visibilityKind),
              DeleteRepositoryComponent.apply(props.ctl, props.repositoryName))
        case _ => Seq.empty
      }
      <.section(
        <.h1("Settings"),
        components
      )
    }
  }

  private val component = ReactComponentB[Props]("RepositorySettings")
    .initialState(State(None))
    .renderBackend[Backend]
    .componentWillMount($ => $.backend.getRepository($.props.repositoryName))
    .build

  def apply(ctl: RouterCtl[DashboardRoute], repositoryName: String) =
    component.apply(Props(ctl, repositoryName))
}
