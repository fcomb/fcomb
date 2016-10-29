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

package io.fcomb.models

import io.fcomb.models.acl.Role
import java.time.OffsetDateTime

final case class OrganizationGroup(
    id: Option[Int],
    organizationId: Int,
    name: String,
    role: Role,
    createdAt: OffsetDateTime,
    updatedAt: Option[OffsetDateTime]
) extends ModelWithAutoIntPk {
  def withId(id: Int) = this.copy(id = Some(id))
}

object OrganizationGroup {
  val nameRegEx = """[A-Za-z][\w\-\.]*""".r
}

final case class OrganizationGroupUser(
    groupId: Int,
    userId: Int
)
