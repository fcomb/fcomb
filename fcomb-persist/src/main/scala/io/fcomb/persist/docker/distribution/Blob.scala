package io.fcomb.persist.docker.distribution

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{Blob ⇒ MBlob, _}
import io.fcomb.persist._
import io.fcomb.validations._
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.Manifest
import scalaz._, Scalaz._
import java.time.ZonedDateTime
import java.util.UUID

class BlobTable(tag: Tag) extends Table[MBlob](tag, "docker_distribution_blobs") with PersistTableWithUuidPk {
  def imageId = column[Long]("image_id")
  def digest = column[Option[String]]("digest")
  def length = column[Long]("length")
  def state = column[BlobState.BlobState]("state")
  def createdAt = column[ZonedDateTime]("created_at")
  def uploadedAt = column[Option[ZonedDateTime]]("uploaded_at")

  def * =
    (id, imageId, digest, length, state, createdAt, uploadedAt) <>
      ((MBlob.apply _).tupled, MBlob.unapply)
}

object Blob extends PersistModelWithUuidPk[MBlob, BlobTable] {
  val table = TableQuery[BlobTable]

  def create(imageId: Long)(
    implicit
    ec: ExecutionContext
  ) =
    super.create(MBlob(
      id = Some(UUID.randomUUID()),
      state = BlobState.Created,
      imageId = imageId,
      digest = None,
      length = 0,
      createdAt = ZonedDateTime.now(),
      uploadedAt = None
    ))

  def createByImageName(name: String, userId: Long)(
    implicit
    ec: ExecutionContext,
    m:  Manifest[MBlob]
  ): Future[ValidationModel] =
    (for {
      imageId ← eitherT(Image.findIdOrCreateByName(name, userId))
      blob ← eitherT(create(imageId))
    } yield blob).run.map(_.validation)
}
