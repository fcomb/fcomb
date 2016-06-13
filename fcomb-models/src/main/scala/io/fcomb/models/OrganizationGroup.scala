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

import java.time.ZonedDateTime

sealed trait OrganizationGroupKind extends EnumItem

object OrganizationGroupKind extends Enum[OrganizationGroupKind] {
  final case object Admin   extends OrganizationGroupKind
  final case object Creator extends OrganizationGroupKind
  final case object Member  extends OrganizationGroupKind

  val values = findValues
}

final case class OrganizationGroup(
    id: Option[Long] = None,
    organizationId: Long,
    name: String,
    kind: OrganizationGroupKind,
    createdByUserId: Long,
    createdAt: ZonedDateTime,
    updatedAt: Option[ZonedDateTime]
)
    extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}

final case class OrganizationGroupUser(
    groupId: Long,
    userId: Long
)
