package io.fcomb.models.docker.distribution

import io.fcomb.models.{Enum, EnumItem, ModelWithUuidPk}
import cats.syntax.eq._
import java.time.ZonedDateTime
import java.util.UUID

sealed trait ImageBlobState extends EnumItem

object ImageBlobState extends Enum[ImageBlobState] {
  final case object Created extends ImageBlobState
  final case object Uploading extends ImageBlobState
  final case object Uploaded extends ImageBlobState

  val values = findValues
}

case class ImageBlob(
    id:           Option[UUID]          = None,
    imageId:      Long,
    state:        ImageBlobState,
    sha256Digest: Option[String],
    contentType:  String,
    length:       Long,
    createdAt:    ZonedDateTime,
    uploadedAt:   Option[ZonedDateTime]
) extends ModelWithUuidPk {
  def withPk(id: UUID) = this.copy(id = Some(id))

  def isCreated =
    state === ImageBlobState.Created

  def isUploaded =
    state === ImageBlobState.Uploaded
}
