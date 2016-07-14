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

package io.fcomb.persist.docker.distribution

import cats.data.Xor
import io.fcomb.Db.db
import io.fcomb.FcombPostgresProfile.api._
import io.fcomb.models.OwnerKind
import io.fcomb.models.docker.distribution.ImageManifest.{emptyTarSha256Digest, emptyTar}
import io.fcomb.models.docker.distribution.{ImageBlobState, ImageBlob, BlobFileState}
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.{PersistTableWithUuidPk, PersistModelWithUuidPk}
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

class ImageBlobTable(tag: Tag)
    extends Table[ImageBlob](tag, "dd_image_blobs")
    with PersistTableWithUuidPk {
  def imageId     = column[Int]("image_id")
  def state       = column[ImageBlobState]("state")
  def digest      = column[Option[String]]("digest")
  def contentType = column[String]("content_type")
  def length      = column[Long]("length")
  def createdAt   = column[ZonedDateTime]("created_at")
  def uploadedAt  = column[Option[ZonedDateTime]]("uploaded_at")

  def * =
    (id, imageId, state, digest, contentType, length, createdAt, uploadedAt) <>
      ((ImageBlob.apply _).tupled, ImageBlob.unapply)
}

object ImageBlobsRepo extends PersistModelWithUuidPk[ImageBlob, ImageBlobTable] {
  val table = TableQuery[ImageBlobTable]

  val `application/octet-stream` = "application/octet-stream"

  private def mapContentType(contentType: String): String = {
    if (contentType == "none/none") `application/octet-stream`
    else contentType
  }

  def mount(fromImageId: Int, toImageId: Int, digest: String, userId: Int)(
      implicit ec: ExecutionContext
  ): Future[Option[ImageBlob]] =
    runInTransaction(TransactionIsolation.ReadCommitted) {
      findByImageIdAndDigestCompiled((fromImageId, digest)).result.headOption.flatMap {
        case Some(blob) =>
          findByImageIdAndDigestCompiled((toImageId, digest)).result.headOption.flatMap {
            case Some(b) => DBIO.successful(Some(b))
            case None =>
              val timeNow = ZonedDateTime.now()
              createDBIO(
                ImageBlob(
                  id = Some(UUID.randomUUID()),
                  state = ImageBlobState.Uploaded,
                  imageId = toImageId,
                  digest = blob.digest,
                  contentType = mapContentType(blob.contentType),
                  length = blob.length,
                  createdAt = timeNow,
                  uploadedAt = None
                )).map(Some(_))
          }
        case _ => DBIO.successful(None)
      }
    }

  def create(imageId: Int, contentType: String)(implicit ec: ExecutionContext) =
    super.create(
      ImageBlob(
        id = Some(UUID.randomUUID()),
        state = ImageBlobState.Created,
        imageId = imageId,
        digest = None,
        contentType = mapContentType(contentType),
        length = 0L,
        createdAt = ZonedDateTime.now(),
        uploadedAt = None
      ))

  override def createDBIO(blob: ImageBlob)(implicit ec: ExecutionContext): ModelDBIO = {
    for {
      res <- tableWithPk += blob
      _   <- BlobFilesRepo.createDBIO(res.getId)
    } yield res
  }

  private lazy val findByImageIdAndUuidCompiled = Compiled {
    (imageId: Rep[Int], uuid: Rep[UUID]) =>
      table.filter { q =>
        q.imageId === imageId && q.id === uuid
      }.take(1)
  }

  def findByImageIdAndUuid(imageId: Int, uuid: UUID)(implicit ec: ExecutionContext) =
    db.run(findByImageIdAndUuidCompiled((imageId, uuid)).result.headOption)

  private lazy val findByImageIdAndDigestCompiled = Compiled {
    (imageId: Rep[Int], digest: Rep[String]) =>
      table.filter { q =>
        q.imageId === imageId && q.digest === digest
      }.take(1)
  }

  def findByImageIdAndDigest(imageId: Int, digest: String)(implicit ec: ExecutionContext) =
    db.run(findByImageIdAndDigestCompiled((imageId, digest)).result.headOption)

  private def findByImageIdAndDigestsScopeDBIO(imageId: Int, digests: Set[String]) =
    table.filter { q =>
      q.imageId === imageId && q.digest.inSetBind(digests)
    }

  def findIdsWithDigestByImageIdAndDigests(imageId: Int, digests: Set[String])(
      implicit ec: ExecutionContext
  ): Future[Seq[(UUID, Option[String], Long)]] =
    db.run {
      findByImageIdAndDigestsScopeDBIO(imageId, digests)
        .map(t => (t.pk, t.digest, t.length))
        .result
    }

  def findByImageIdAndDigests(imageId: Int, digests: Set[String])(implicit ec: ExecutionContext) =
    db.run(findByImageIdAndDigestsScopeDBIO(imageId, digests).result)

  private lazy val existUploadedByImageIdAndDigestCompiled = Compiled {
    (imageId: Rep[Int], digest: Rep[String], exceptId: Rep[UUID]) =>
      table.filter { q =>
        q.pk =!= exceptId && q.imageId === imageId && q.digest === digest &&
        q.state === (ImageBlobState.Uploaded: ImageBlobState)
      }.exists
  }

  def completeUploadOrDelete(id: UUID, imageId: Int, length: Long, digest: String)(
      implicit ec: ExecutionContext): Future[BlobFileState] = {
    runInTransaction(TransactionIsolation.ReadCommitted) {
      existUploadedByImageIdAndDigestCompiled((imageId, digest, id)).result.flatMap { exists =>
        if (exists) {
          for {
            _   <- destroyDBIO(id)
            res <- BlobFilesRepo.markAsDuplicateDBIO(id)
          } yield res
        } else {
          for {
            _ <- table
                  .filter(_.pk === id)
                  .map(t => (t.state, t.length, t.digest, t.uploadedAt))
                  .update(
                    (
                      ImageBlobState.Uploaded,
                      length,
                      Some(digest),
                      Some(ZonedDateTime.now())
                    ))
            res <- BlobFilesRepo.markDBIO(id, digest)
          } yield res
        }
      }
    }
  }

  def updateState(id: UUID, length: Long, digest: String, state: ImageBlobState)(
      implicit ec: ExecutionContext
  ) = db.run {
    table
      .filter(_.id === id)
      .map(t => (t.state, t.length, t.digest))
      .update((state, length, Some(digest)))
  }

  override def destroy(id: UUID)(implicit ec: ExecutionContext): Future[Int] = {
    db.run {
      for {
        res <- destroyDBIO(id)
        _   <- BlobFilesRepo.markOrDestroyDBIO(id)
      } yield res
    }
  }

  def duplicateDigestsByImageIdDBIO(imageId: Int) = {
    table
      .join(table)
      .on(_.digest === _.digest)
      .filter {
        case (t, tt) =>
          t.digest.nonEmpty && t.imageId === imageId && tt.imageId =!= imageId
      }
      .map(_._1.digest)
  }

  def duplicateDigestsByOrganizationIdDBIO(organizationId: Int) = {
    table
      .join(ImageBlobsRepo.table)
      .on(_.digest === _.digest)
      .join(ImagesRepo.table)
      .on(_._1.imageId === _.id)
      .join(ImagesRepo.table)
      .on(_._1._2.imageId === _.id)
      .filter {
        case (((t, ibt), it), it2) =>
          t.digest.nonEmpty &&
            t.imageId =!= ibt.imageId &&
            it.ownerId === organizationId &&
            it.ownerKind === (OwnerKind.Organization: OwnerKind) &&
            !(it2.ownerId === organizationId &&
                  it2.ownerKind === (OwnerKind.Organization: OwnerKind))
      }
      .map(_._1._1._1.digest)
      .distinct
      .subquery
  }

  def uniqueDigestsByOrganizationIdDBIO(organizationId: Int) = {
    table
      .join(ImagesRepo.table)
      .on(_.imageId === _.id)
      .filter {
        case (t, it) =>
          t.digest.nonEmpty &&
            it.ownerId === organizationId &&
            it.ownerKind === (OwnerKind.Organization: OwnerKind) &&
            !t.digest.in(duplicateDigestsByOrganizationIdDBIO(organizationId))
      }
      .map(_._1.digest)
      .subquery
  }

  def findIdsWithEmptyDigestsByOrganizationIdDBIO(organizationId: Int) = {
    table
      .join(ImagesRepo.table)
      .on(_.imageId === _.id)
      .filter {
        case (t, it) =>
          it.ownerId === organizationId &&
            it.ownerKind === (OwnerKind.Organization: OwnerKind) &&
            t.digest.isEmpty
      }
      .map(_._1.pk)
  }

  def destroyByImageIdDBIO(imageId: Int)(implicit ec: ExecutionContext) = {
    for {
      _   <- BlobFilesRepo.markOrDestroyByImageIdDBIO(imageId)
      res <- table.filter(_.imageId === imageId).delete
    } yield res
  }

  def destroyOutdatedUploadsDBIO(until: Rep[ZonedDateTime]) = {
    table.filter { q =>
      q.createdAt <= until && q.state =!= (ImageBlobState.Uploaded: ImageBlobState)
    }
  }

  def destroyOutdatedUploads(until: ZonedDateTime)(implicit ec: ExecutionContext) = {
    runInTransaction(TransactionIsolation.ReadCommitted) {
      for {
        _   <- BlobFilesRepo.destroyOutdatedUploadsDBIO(until)
        res <- destroyOutdatedUploadsDBIO(until).delete
      } yield res
    }
  }

  def destroyByOrganizationIdDBIO(organizationId: Int)(implicit ec: ExecutionContext) = {
    for {
      _ <- BlobFilesRepo.markOrDestroyByOrganizationIdDBIO(organizationId)
      res <- table
              .filter(_.imageId.in(ImagesRepo.findIdsByOrganizationIdDBIO(organizationId)))
              .delete
    } yield res
  }

  def findByIds(ids: List[UUID]) =
    db.run(table.filter(_.id.inSetBind(ids)).result)

  def createEmptyTarIfNotExists(imageId: Int)(implicit ec: ExecutionContext): Future[Unit] =
    db.run {
      findByImageIdAndDigestCompiled((imageId, emptyTarSha256Digest)).result.headOption.flatMap {
        case Some(_) => DBIO.successful(())
        case None =>
          val blob = ImageBlob(
            id = Some(UUID.randomUUID()),
            state = ImageBlobState.Uploaded,
            imageId = imageId,
            digest = Some(emptyTarSha256Digest),
            contentType = `application/octet-stream`,
            length = emptyTar.length.toLong,
            createdAt = ZonedDateTime.now(),
            uploadedAt = None
          )
          table += blob
      }.map(_ => ())
    }

  def tryDestroy(id: UUID)(implicit ec: ExecutionContext): Future[Xor[String, Unit]] =
    runInTransaction(TransactionIsolation.ReadCommitted) {
      ImageManifestLayersRepo.isBlobLinkedCompiled(id).result.flatMap { isLinked =>
        if (isLinked) DBIO.successful(Xor.left("blob is linked with manifest"))
        else findByIdQuery(id).delete.map(_ => Xor.right(()))
      }
    }.recover { case _ => Xor.right(()) }

  private lazy val existByDigestCompiled = Compiled { digest: Rep[String] =>
    table.filter(_.digest === digest).exists
  }

  def existByDigest(digest: String)(implicit ec: ExecutionContext): Future[Boolean] =
    db.run(existByDigestCompiled(digest).result)
}
