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

import io.fcomb.models.common.{Enum, EnumItem}
import java.time.ZonedDateTime

sealed trait ApplicationState extends EnumItem

object ApplicationState extends Enum[ApplicationState] {
  final case object Disabled extends EnumItem
  final case object Enabled  extends EnumItem

  val values = findValues
}

final case class Application(
    id: Option[Long] = None,
    name: String,
    state: ApplicationState,
    token: String,
    ownerId: Long,
    ownerKind: OwnerKind,
    createdAt: ZonedDateTime,
    updatedAt: Option[ZonedDateTime]
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
