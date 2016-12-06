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

import akka.stream.scaladsl.Source
import io.fcomb.Db.db
import io.fcomb.models.docker.distribution.{BlobFile, BlobFileState}
import io.fcomb.models.errors.Errors
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.Implicits._
import io.fcomb.PostgresProfile.api._
import io.fcomb.validation.DBIOException
import java.time.OffsetDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

final class BlobFileTable(tag: Tag) extends Table[BlobFile](tag, "dd_blob_files") {
  def uuid       = column[UUID]("uuid", O.PrimaryKey)
  def digest     = column[Option[String]]("digest")
  def state      = column[BlobFileState]("state")
  def retryCount = column[Int]("retry_count")
  def createdAt  = column[OffsetDateTime]("created_at")
  def updatedAt  = column[Option[OffsetDateTime]]("updated_at")
  def retriedAt  = column[Option[OffsetDateTime]]("retried_at")

  def * =
    (uuid, digest, state, retryCount, createdAt, updatedAt, retriedAt) <>
      (BlobFile.tupled, BlobFile.unapply)
}

object BlobFilesRepo {
  val table = TableQuery[BlobFileTable]

  def createDBIO(uuid: UUID) =
    table += BlobFile(
      uuid = uuid,
      digest = None,
      state = BlobFileState.Available,
      retryCount = 0,
      createdAt = OffsetDateTime.now,
      updatedAt = None,
      retriedAt = None
    )

  // remove only upload file (with uuid format name)
  def markAsDuplicateDBIO(uuid: UUID)(
      implicit ec: ExecutionContext): DBIOAction[BlobFileState, NoStream, Effect.Write] =
    findByUuidDBIO(uuid)
      .map(t => (t.state, t.digest))
      .update((BlobFileState.Deleting, None))
      .map(_ => BlobFileState.Deleting)

  private lazy val isDuplicateByDigestCompiled = Compiled {
    (uuid: Rep[UUID], digest: Rep[String]) =>
      table.filter(t => t.digest === digest && t.uuid =!= uuid).map(_.state).take(1)
  }

  private def isDuplicateByDigestDBIO(uuid: UUID, digest: String) =
    isDuplicateByDigestCompiled((uuid, digest.toLowerCase)).result.headOption

  /** Remove uploaded file if a digest is not unique or file with the same digest is deleting
    */
  def markDBIO(uuid: UUID, digest: String)(
      implicit ec: ExecutionContext): DBIOAction[BlobFileState, NoStream, Effect.All] =
    isDuplicateByDigestDBIO(uuid, digest).flatMap {
      case Some(BlobFileState.Available) => markAsDuplicateDBIO(uuid)
      case Some(BlobFileState.Deleting) =>
        val e = Errors.internal(
          "Blob file with the same digest has been queued to remove by GC. Please, try again.")
        DBIO.failed(DBIOException(e))
      case _ =>
        findByUuidDBIO(uuid).map(_.digest).update(Some(digest)).map(_ => BlobFileState.Available)
    }

  def findByUuidDBIO(uuid: Rep[UUID]) =
    table.filter(_.uuid === uuid)

  lazy val findByUuidCompiled = Compiled { uuid: Rep[UUID] =>
    findByUuidDBIO(uuid)
  }

  def markOrDestroyDBIO(uuid: UUID)(
      implicit ec: ExecutionContext): DBIOAction[Unit, NoStream, Effect.All] =
    findByUuidCompiled(uuid).result.headOption.flatMap {
      case Some(bf) =>
        (bf.digest match {
          case Some(digest) => isDuplicateByDigestDBIO(uuid, digest).map(_.nonEmpty)
          case _            => DBIO.successful(false)
        }).flatMap {
            case true => findByUuidDBIO(uuid).delete
            case _    => findByUuidDBIO(uuid).map(_.state).update(BlobFileState.Deleting)
          }
          .map(_ => ())
      case _ => DBIO.successful(())
    }

  def markOrDestroyByImageIdDBIO(imageId: Int)(implicit ec: ExecutionContext) =
    table
      .filter { t =>
        t.uuid.in(ImageBlobsRepo.findIdsByImageIdDBIO(imageId)) &&
        (t.digest.isEmpty ||
        !t.digest.in(ImageBlobsRepo.duplicateDigestsByImageIdDBIO(imageId)))
      }
      .map(_.state)
      .update(BlobFileState.Deleting)

  def markOrDestroyByOrganizationIdDBIO(orgId: Int)(implicit ec: ExecutionContext) =
    table
      .filter { q =>
        q.uuid.in(ImageBlobsRepo.findIdsWithEmptyDigestsByOrganizationIdDBIO(orgId)) ||
        q.digest.in(ImageBlobsRepo.uniqueDigestsByOrganizationIdDBIO(orgId))
      }
      .map(_.state)
      .update(BlobFileState.Deleting)

  def markOutdatedUploadsDBIO(until: Rep[OffsetDateTime]) =
    table
      .filter(_.uuid.in(ImageBlobsRepo.findOutdatedUploadsIdDBIO(until)))
      .map(_.state)
      .update(BlobFileState.Deleting)

  def destroy(uuids: Seq[UUID]) =
    if (uuids.isEmpty) Future.unit
    else db.run(table.filter(_.uuid.inSetBind(uuids)).delete)

  def updateRetryCount(uuids: Seq[UUID]) =
    if (uuids.isEmpty) Future.unit
    else {
      val retriedAt     = Some(OffsetDateTime.now())
      val uuidCol       = table.baseTableRow.uuid.toString()
      val retryCountCol = table.baseTableRow.retryCount.toString()
      val retriedAtCol  = table.baseTableRow.retriedAt.toString()
      db.run {
        // TODO: replace it when it is ready: https://github.com/slick/slick/issues/497
        sqlu"""
        UPDATE #${table.baseTableRow.tableName}
            SET #$retryCountCol = #$retryCountCol + 1,
                #$retriedAtCol = $retriedAt
            WHERE #$uuidCol IN ($uuids)
        """
      }
    }

  private lazy val findDeletingCompiled = Compiled {
    table.filter(_.state === (BlobFileState.Deleting: BlobFileState))
  }

  def findDeleting() =
    Source.fromPublisher(db.stream(findDeletingCompiled.result))
}
