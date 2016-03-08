package io.fcomb.models.docker.distribution

import io.fcomb.models.ModelWithUuidPk
import java.util.UUID
import java.time.ZonedDateTime

object BlobState extends Enumeration {
  type BlobState = Value

  val Created = Value("created")
  val Uploading = Value("uploading")
  val Uploaded = Value("uploaded")
}

case class Blob(
  id:           Option[UUID]          = None,
  imageId:      Long,
  sha256Digest: Option[String],
  length:       Long,
  state:        BlobState.BlobState,
  createdAt:    ZonedDateTime,
  uploadedAt:   Option[ZonedDateTime]
) extends ModelWithUuidPk {
  def isCreated =
    state == BlobState.Created
}
