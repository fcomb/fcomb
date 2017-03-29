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

package io.fcomb.models.docker.distribution

import io.fcomb.models.ModelWithUuidPk
import io.fcomb.models.common.{Enum, EnumItem}
import cats.syntax.eq._
import java.time.OffsetDateTime
import java.util.UUID

sealed trait ImageBlobState extends EnumItem

object ImageBlobState extends Enum[ImageBlobState] {
  case object Created   extends ImageBlobState
  case object Uploading extends ImageBlobState
  case object Uploaded  extends ImageBlobState

  val values = findValues
}

case class ImageBlob(
    id: Option[UUID] = None,
    imageId: Int,
    state: ImageBlobState,
    digest: Option[String],
    contentType: String,
    length: Long,
    createdAt: OffsetDateTime,
    uploadedAt: Option[OffsetDateTime]
) extends ModelWithUuidPk {
  def withId(id: UUID) = this.copy(id = Some(id))

  def isCreated =
    state === ImageBlobState.Created

  def isUploaded =
    state === ImageBlobState.Uploaded
}
