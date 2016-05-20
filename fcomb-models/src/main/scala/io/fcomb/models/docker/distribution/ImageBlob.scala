package io.fcomb.models.docker.distribution

import io.fcomb.models.ModelWithUuidPk
import cats.Eq
import cats.syntax.eq._
import java.time.ZonedDateTime
import java.util.UUID

object ImageBlobState extends Enumeration {
  type ImageBlobState = Value

  val Created = Value("created")
  val Uploading = Value("uploading")
  val Uploaded = Value("uploaded")

  implicit val valueEq: Eq[ImageBlobState] = Eq.fromUniversalEquals
}

case class ImageBlob(
  id:           Option[UUID]                  = None,
  imageId:      Long,
  state:        ImageBlobState.ImageBlobState,
  sha256Digest: Option[String],
  contentType:  String,
  length:       Long,
  createdAt:    ZonedDateTime,
  uploadedAt:   Option[ZonedDateTime]
)
    extends ModelWithUuidPk {
  def withPk(id: UUID) = this.copy(id = Some(id))

  def isCreated =
    state === ImageBlobState.Created

  def isUploaded =
    state === ImageBlobState.Uploaded
}
