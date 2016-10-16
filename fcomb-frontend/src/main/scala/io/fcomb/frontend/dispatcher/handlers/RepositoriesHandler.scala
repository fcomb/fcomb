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

package io.fcomb.frontend.dispatcher.handlers

import diode.data.{PotMap, Ready}
import diode.{ActionHandler, ActionResult, ModelRW}
import io.fcomb.frontend.api.Rpc
import io.fcomb.frontend.dispatcher.actions._
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

final class RepositoriesHandler[M](modelRW: ModelRW[M, PotMap[String, RepositoryResponse]])
    extends ActionHandler(modelRW) {
  val pf: PartialFunction[RepositoryAction, ActionResult[M]] = {
    case action: UpdateRepository =>
      ActionUtils.handleWithKey(action, value, action.slug) { slug =>
        val updateEffect = action.effect(Rpc.getRepository(slug))(identity)
        action.handleWith(this, updateEffect)(ActionUtils.mapHandler(slug))
      }
    case UpsertRepository(repo) => updated(value + ((repo.slug, Ready(repo))))
  }

  protected def handle = { case action: RepositoryAction => pf(action) }
}
