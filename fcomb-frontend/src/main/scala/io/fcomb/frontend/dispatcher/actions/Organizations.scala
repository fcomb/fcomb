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

package io.fcomb.frontend.dispatcher.actions

import diode.Action
import diode.data.{AsyncAction, Pot, PotState}
import io.fcomb.rpc.OrganizationResponse
import scala.util.{Failure, Try}

sealed trait OrganizationAction extends Action

final case class UpdateOrganization(
    slug: String,
    state: PotState = PotState.PotEmpty,
    result: Try[Pot[OrganizationResponse]] = Failure(new AsyncAction.PendingException)
) extends AsyncAction[Pot[OrganizationResponse], UpdateOrganization]
    with OrganizationAction {
  def next(newState: PotState, newValue: Try[Pot[OrganizationResponse]]) =
    UpdateOrganization(slug, newState, newValue)
}

final case class UpsertOrganization(org: OrganizationResponse) extends OrganizationAction
