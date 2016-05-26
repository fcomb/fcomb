package io.fcomb.models.docker.distribution

import io.fcomb.models.ModelWithAutoLongPk
import java.time.ZonedDateTime
import java.util.UUID

final case class ImageManifestSchemaV2Details(
  configBlobId: Option[UUID],
  jsonBlob:     String
)

final case class ImageManifest(
    id:               Option[Long]                         = None,
    sha256Digest:     String,
    imageId:          Long,
    tags:             List[String],
    layersBlobId:     List[UUID],
    schemaVersion:    Int,
    schemaV1JsonBlob: String,
    schemaV2Details:  Option[ImageManifestSchemaV2Details],
    createdAt:        ZonedDateTime,
    updatedAt:        Option[ZonedDateTime]
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}

object ImageManifest {
  val sha256Prefix = "sha256:"

  val emptyTarSha256Digest =
    "a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4"

  val emptyTarSha256DigestFull = s"$sha256Prefix$emptyTarSha256Digest"

  val emptyTar = Array[Byte](31, -117, 8, 0, 0, 9, 110, -120, 0, -1, 98, 24, 5,
    -93, 96, 20, -116, 88, 0, 8, 0, 0, -1, -1, 46, -81, -75, -17, 0, 4, 0, 0)
}
