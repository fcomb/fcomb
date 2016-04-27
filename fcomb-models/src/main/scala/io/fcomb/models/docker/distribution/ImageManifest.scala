package io.fcomb.models.docker.distribution

import io.fcomb.models.ModelWithAutoLongPk
import java.time.ZonedDateTime
import java.util.UUID

final case class ImageManifest(
    id:           Option[Long]          = None,
    sha256Digest: String,
    imageId:      Long,
    tags:         List[String],
    configBlobId: UUID,
    layersBlobId: List[UUID],
    createdAt:    ZonedDateTime,
    updatedAt:    Option[ZonedDateTime]
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
