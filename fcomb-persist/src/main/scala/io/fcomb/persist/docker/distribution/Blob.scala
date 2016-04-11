package io.fcomb.persist.docker.distribution

import io.fcomb.Db.db
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
  def sha256Digest = column[Option[String]]("sha256_digest")
  def length = column[Long]("length")
  def state = column[BlobState.BlobState]("state")
  def createdAt = column[ZonedDateTime]("created_at")
  def uploadedAt = column[Option[ZonedDateTime]]("uploaded_at")

  def * =
    (id, imageId, sha256Digest, length, state, createdAt, uploadedAt) <>
      ((MBlob.apply _).tupled, MBlob.unapply)
}

object Blob extends PersistModelWithUuidPk[MBlob, BlobTable] {
  val table = TableQuery[BlobTable]

  override def mapModel(b: MBlob) = b.copy(
    sha256Digest = b.sha256Digest.map(_.toLowerCase)
  )

  def create(imageId: Long)(
    implicit
    ec: ExecutionContext
  ) =
    super.create(MBlob(
      id = Some(UUID.randomUUID()),
      state = BlobState.Created,
      imageId = imageId,
      sha256Digest = None,
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

  private def imageScope() =
    table.join(Image.table).on(_.imageId === _.id)

  private val findByImageAndUuidCompiled = Compiled {
    (name: Rep[String], uuid: Rep[UUID]) ⇒
      imageScope
        .filter { q ⇒
          q._2.name === name && q._1.id === uuid
        }
        .take(1)
  }

  def findByImageAndUuid(image: String, uuid: UUID)(
    implicit
    ec: ExecutionContext
  ) =
    db.run(findByImageAndUuidCompiled(image, uuid).result.headOption)

  private val findByImageAndDigestCompiled = Compiled {
    (name: Rep[String], digest: Rep[String]) ⇒
      imageScope
        .filter { q ⇒
          q._2.name === name &&
            q._1.sha256Digest === digest.toLowerCase
        }
        .take(1)
  }

  def findByImageAndDigest(image: String, digest: String)(
    implicit
    ec: ExecutionContext
  ) =
    db.run(findByImageAndDigestCompiled(image, digest).result.headOption)

  def findByImageIdAndDigests(imageId: Long, digests: List[String])(
    implicit
    ec: ExecutionContext
  ) =
    db.run(table.filter { q ⇒
      q.imageId === imageId && q.sha256Digest.inSetBind(digests)
    }.result)

  def updateState(
    id:     UUID,
    length: Long,
    digest: Option[String],
    state:  BlobState.BlobState
  )(
    implicit
    ec: ExecutionContext
  ) = db.run {
    table
      .filter(_.id === id)
      .map(t ⇒ (t.state, t.length, t.sha256Digest))
      .update((state, length, digest))
  }

  def findByIds(ids: List[UUID]) =
    db.run(table.filter(_.id.inSetBind(ids)).result)
}
