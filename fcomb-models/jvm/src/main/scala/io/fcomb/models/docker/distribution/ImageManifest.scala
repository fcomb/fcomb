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

import io.fcomb.models.ModelWithAutoIntPk
import java.time.ZonedDateTime
import java.util.UUID
import scala.util.control.NoStackTrace

final class EmptySchemaV2JsonBlobException extends NoStackTrace

final case class ImageManifestSchemaV2Details(
    configBlobId: Option[UUID],
    jsonBlob: String
)

final case class ImageManifest(
    id: Option[Int],
    sha256Digest: String,
    imageId: Int,
    tags: List[String],
    layersBlobId: List[UUID],
    schemaVersion: Int,
    schemaV1JsonBlob: String,
    schemaV2Details: Option[ImageManifestSchemaV2Details],
    createdAt: ZonedDateTime,
    updatedAt: Option[ZonedDateTime]
) extends ModelWithAutoIntPk {
  def withPk(id: Int) = this.copy(id = Some(id))

  def getSchemaV2JsonBlob() =
    schemaV2Details.map(_.jsonBlob).getOrElse(throw new EmptySchemaV2JsonBlobException)
}

object ImageManifest {
  val sha256Prefix = "sha256:"

  val emptyTarSha256Digest = "a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"

  val emptyTarSha256DigestFull = s"$sha256Prefix$emptyTarSha256Digest"

  val emptyTar = Array[Byte](31,
                             -117,
                             8,
                             0,
                             0,
                             9,
                             110,
                             -120,
                             0,
                             -1,
                             98,
                             24,
                             5,
                             -93,
                             96,
                             20,
                             -116,
                             88,
                             0,
                             8,
                             0,
                             0,
                             -1,
                             -1,
                             46,
                             -81,
                             -75,
                             -17,
                             0,
                             4,
                             0,
                             0)
}
