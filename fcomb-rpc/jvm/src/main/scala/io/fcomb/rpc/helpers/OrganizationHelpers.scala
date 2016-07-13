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

package io.fcomb.rpc.helpers

import io.fcomb.models.Organization
import io.fcomb.rpc.OrganizationResponse
import io.fcomb.rpc.helpers.time.Implicits._

object OrganizationHelpers {
  def responseFrom(org: Organization, isPublic: Boolean): OrganizationResponse = {
    OrganizationResponse(
      id = org.getId(),
      name = org.name,
      ownerUserId = if (isPublic) None else Some(org.ownerUserId),
      createdAt = org.createdAt.toIso8601,
      updatedAt = org.updatedAt.map(_.toIso8601)
    )
  }
}
