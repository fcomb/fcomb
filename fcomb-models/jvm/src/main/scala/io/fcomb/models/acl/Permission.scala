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

package io.fcomb.models.acl

import io.fcomb.models.ModelWithAutoIntPk
import io.fcomb.models.common.{Enum, EnumItem}
import java.time.ZonedDateTime

sealed trait SourceKind extends EnumItem

object SourceKind extends Enum[SourceKind] {
  final case object DockerDistributionImage extends SourceKind

  val values = findValues
}

sealed trait MemberKind extends EnumItem

object MemberKind extends Enum[MemberKind] {
  final case object Group extends MemberKind
  final case object User  extends MemberKind

  val values = findValues
}

final case class Permission(
    id: Option[Int],
    sourceId: Int,
    sourceKind: SourceKind,
    memberId: Int,
    memberKind: MemberKind,
    action: Action,
    createdAt: ZonedDateTime,
    updatedAt: Option[ZonedDateTime]
) extends ModelWithAutoIntPk {
  def withPk(id: Int) = this.copy(id = Some(id))
}
