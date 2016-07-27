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

package io.fcomb.models.docker.distribution

import io.fcomb.models.common.{Enum, EnumItem}
import java.time.OffsetDateTime
import java.util.UUID

sealed trait BlobFileState extends EnumItem

object BlobFileState extends Enum[BlobFileState] {
  final case object Available extends BlobFileState
  final case object Deleting  extends BlobFileState

  val values = findValues
}

final case class BlobFile(
    uuid: UUID,
    digest: Option[String],
    state: BlobFileState,
    retryCount: Int,
    createdAt: OffsetDateTime,
    updatedAt: Option[OffsetDateTime],
    retriedAt: Option[OffsetDateTime]
)
