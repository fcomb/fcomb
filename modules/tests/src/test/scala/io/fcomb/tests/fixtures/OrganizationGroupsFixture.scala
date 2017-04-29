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

package io.fcomb.tests.fixtures

import io.fcomb.models.acl.Role
import io.fcomb.models.OrganizationGroup
import io.fcomb.persist.OrganizationGroupsRepo
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object OrganizationGroupsFixture {
  val name = "test_group"

  def create(name: String = name, orgId: Int, role: Role): Future[OrganizationGroup] =
    OrganizationGroupsRepo
      .create(
        OrganizationGroup(
          id = None,
          organizationId = orgId,
          name = name,
          role = role,
          createdAt = OffsetDateTime.now,
          updatedAt = None
        ))
      .map(Fixtures.get)
}
