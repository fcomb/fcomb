package io.fcomb.persist.docker.distribution

import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{ImageBlob ⇒ MImageBlob, ImageBlobState}
import io.fcomb.persist._
import io.fcomb.validations.eitherT
import scala.concurrent.{ExecutionContext, Future}
import java.time.ZonedDateTime
import java.util.UUID
import cats.std.all._

class ImageBlobTable(tag: Tag) extends Table[MImageBlob](tag, "docker_distribution_image_blobs")
    with PersistTableWithUuidPk {
  def imageId = column[Long]("image_id")
  def state = column[ImageBlobState.ImageBlobState]("state")
  def sha256Digest = column[Option[String]]("sha256_digest")
  def contentType = column[String]("content_type")
  def length = column[Long]("length")
  def createdAt = column[ZonedDateTime]("created_at")
  def uploadedAt = column[Option[ZonedDateTime]]("uploaded_at")

  def * =
    (id, imageId, state, sha256Digest, contentType, length, createdAt, uploadedAt) <>
      ((MImageBlob.apply _).tupled, MImageBlob.unapply)
}

object ImageBlob extends PersistModelWithUuidPk[MImageBlob, ImageBlobTable] {
  val table = TableQuery[ImageBlobTable]

  // private val isPresentCompiled = Compiled {
  //   (imageId: Rep[Long], blobId: Rep[Long]) ⇒
  //     table
  //       .filter { q ⇒
  //         q.imageId === imageId && q.blobId === blobId
  //       }
  //       .exists
  // }

  // // TODO
  // def mount(fromImageName: String, toImageName: String, digest: String)(
  //   implicit
  //   ec: ExecutionContext
  // ): Future[Option[MImageBlob]] = db.run {
  //   findByImageAndDigestCompiled((fromImageName, digest)).result.headOption
  //     .flatMap {
  //       case Some((blob, _)) ⇒
  //         findByImageAndDigestCompiled((toImageName, digest)).result.headOption.flatMap {
  //           case Some((e, _)) ⇒ DBIO.successful(Some(e))
  //           case None ⇒
  //             val timeNow = ZonedDateTime.now()
  //             createDBIO(MImageBlob(
  //               id = Some(UUID.randomUUID()),
  //               state = ImageBlobState.Created,
  //               imageId = ???, // TODO
  //               sha256Digest = None,
  //               contentType = blob.contentType,
  //               length = blob.length,
  //               createdAt = timeNow,
  //               uploadedAt = Some(timeNow)
  //             )).map(Some(_))
  //         }
  //       case None ⇒ DBIO.successful(None)
  //     }
  //     .transactionally
  // }

  def create(imageId: Long, contentType: String)(
    implicit
    ec: ExecutionContext
  ) =
    super.create(MImageBlob(
      id = Some(UUID.randomUUID()),
      state = ImageBlobState.Created,
      imageId = imageId,
      sha256Digest = None,
      contentType = contentType,
      length = 0L,
      createdAt = ZonedDateTime.now(),
      uploadedAt = None
    ))

  // TODO
  def createByImageName(name: String, userId: Long, contentType: String)(
    implicit
    ec: ExecutionContext,
    m:  Manifest[MImageBlob]
  ): Future[ValidationModel] =
    (for {
      imageId ← eitherT(Image.findIdOrCreateByName(name, userId))
      blob ← eitherT(create(imageId, contentType))
    } yield blob).toValidated

  private val findByImageIdAndUuidCompiled = Compiled {
    (imageId: Rep[Long], uuid: Rep[UUID]) ⇒
      table
        .filter { q ⇒
          q.imageId === imageId && q.id === uuid
        }
        .take(1)
  }

  def findByImageIdAndUuid(imageId: Long, uuid: UUID)(
    implicit
    ec: ExecutionContext
  ) =
    db.run(findByImageIdAndUuidCompiled(imageId, uuid).result.headOption)

  private val findByImageIdAndDigestCompiled = Compiled {
    (imageId: Rep[Long], digest: Rep[String]) ⇒
      table
        .filter { q ⇒
          q.imageId === imageId &&
            q.sha256Digest === digest
        }
        .take(1)
  }

  def findByImageIdAndDigest(imageId: Long, digest: String)(
    implicit
    ec: ExecutionContext
  ) =
    db.run(findByImageIdAndDigestCompiled(imageId, digest).result.headOption)

  def findByImageIdAndDigests(imageId: Long, digests: List[String])(
    implicit
    ec: ExecutionContext
  ) =
    db.run(table.filter { q ⇒
      q.imageId === imageId && q.sha256Digest.inSetBind(digests)
    }.result)

  // TODO: check for blob to exists with the same digest before !!!
  def completeUpload(
    id:     UUID,
    length: Long,
    digest: String
  )(
    implicit
    ec: ExecutionContext
  ) = db.run {
    table
      .filter(_.id === id)
      .map(t ⇒ (t.state, t.length, t.sha256Digest, t.uploadedAt))
      .update((ImageBlobState.Uploaded, length, Some(digest), Some(ZonedDateTime.now())))
  }

  def updateState(
    id:     UUID,
    length: Long,
    digest: String,
    state:  ImageBlobState.ImageBlobState
  )(
    implicit
    ec: ExecutionContext
  ) = db.run {
    table
      .filter(_.id === id)
      .map(t ⇒ (t.state, t.length, t.sha256Digest))
      .update((state, length, Some(digest)))
  }

  def findByIds(ids: List[UUID]) =
    db.run(table.filter(_.id.inSetBind(ids)).result)

  private val findOutdatedUploadsCompiled = Compiled { until: Rep[ZonedDateTime] ⇒
    table.filter { q ⇒
      q.createdAt <= until &&
        (q.state === ImageBlobState.Created || q.state === ImageBlobState.Uploading)
    }
  }

  def findOutdatedUploads(until: ZonedDateTime) =
    db.stream(findOutdatedUploadsCompiled(until).result)
}
