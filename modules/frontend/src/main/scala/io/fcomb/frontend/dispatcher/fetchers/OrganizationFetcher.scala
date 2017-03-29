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

package io.fcomb.frontend.dispatcher.fetchers

import diode.data.Fetch
import diode.{ActionBatch, Dispatcher}
import io.fcomb.frontend.dispatcher.actions.UpdateOrganization

final case class OrganizationFetcher(dispatch: Dispatcher) extends Fetch[String] {
  override def fetch(key: String): Unit =
    dispatch(UpdateOrganization(key))

  override def fetch(keys: Traversable[String]): Unit =
    dispatch(ActionBatch(keys.toSeq.distinct.map(key => UpdateOrganization(key)): _*))
}
