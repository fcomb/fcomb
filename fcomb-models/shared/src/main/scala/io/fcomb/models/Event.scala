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
import org.threeten.bp.ZonedDateTime

sealed trait EventKind extends EnumItem

object EventKind extends Enum[EventKind] {
  case object CreateRepo extends EventKind
  case object PushRepo   extends EventKind

  val values = findValues
}

sealed trait EventDetails {
  val kind: EventKind
}

object EventDetails {
  final case class CreateRepo(
      repoId: Int,
      name: String,
      slug: String,
      kind: EventKind = EventKind.CreateRepo
  ) extends EventDetails

  final case class PushRepo(
      repoId: Int,
      manifestId: Int,
      name: String,
      slug: String,
      reference: String,
      kind: EventKind = EventKind.PushRepo
  ) extends EventDetails
}

final case class Event(
    id: Option[Int] = None,
    kind: EventKind,
    details: EventDetails,
    createdBy: Int,
    createdAt: ZonedDateTime
) extends ModelWithAutoIntPk {
  def withPk(id: Int) = this.copy(id = Some(id))
}
