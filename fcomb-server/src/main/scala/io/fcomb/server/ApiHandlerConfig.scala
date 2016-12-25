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

package io.fcomb.server

import akka.actor.ActorSystem
import akka.stream.Materializer
import io.fcomb.PostgresProfile.api.Database
import io.fcomb.models.Settings
import scala.concurrent.ExecutionContext

final case class ApiHandlerConfig(
    implicit val sys: ActorSystem,
    val mat: Materializer,
    val db: Database,
    val settings: Settings
) {
  implicit val ec: ExecutionContext = sys.dispatcher
}
