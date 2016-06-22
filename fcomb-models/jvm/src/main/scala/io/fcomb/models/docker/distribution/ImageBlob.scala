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

import io.fcomb.models.{Enum, EnumItem, ModelWithUuidPk}
import cats.syntax.eq._
import java.time.ZonedDateTime
import java.util.UUID

sealed trait ImageBlobState extends EnumItem

object ImageBlobState extends Enum[ImageBlobState] {
  final case object Created   extends ImageBlobState
  final case object Uploading extends ImageBlobState
  final case object Uploaded  extends ImageBlobState

  val values = findValues
}

case class ImageBlob(
    id: Option[UUID] = None,
    imageId: Long,
    state: ImageBlobState,
    sha256Digest: Option[String],
    contentType: String,
    length: Long,
    createdAt: ZonedDateTime,
    uploadedAt: Option[ZonedDateTime]
) extends ModelWithUuidPk {
  def withPk(id: UUID) = this.copy(id = Some(id))

  def isCreated =
    state === ImageBlobState.Created

  def isUploaded =
    state === ImageBlobState.Uploaded
}
