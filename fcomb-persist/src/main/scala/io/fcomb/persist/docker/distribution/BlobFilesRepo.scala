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

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{BlobFile, BlobFileState}
import io.fcomb.persist.EnumsMapping._
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext

class BlobFileTable(tag: Tag) extends Table[BlobFile](tag, "dd_blob_files") {
  def uuid       = column[UUID]("uuid", O.PrimaryKey)
  def digest     = column[Option[String]]("digest")
  def state      = column[BlobFileState]("state")
  def retryCount = column[Int]("retry_count")
  def createdAt  = column[ZonedDateTime]("created_at")
  def updatedAt  = column[Option[ZonedDateTime]]("updated_at")
  def retriedAt  = column[Option[ZonedDateTime]]("retried_at")

  def * =
    (uuid, digest, state, retryCount, createdAt, updatedAt, retriedAt) <>
      (BlobFile.tupled, BlobFile.unapply)
}

object BlobFilesRepo {
  val table = TableQuery[BlobFileTable]

  def createDBIO(uuid: UUID) = {
    table += BlobFile(
      uuid = uuid,
      digest = None,
      state = BlobFileState.Available,
      retryCount = 0,
      createdAt = ZonedDateTime.now,
      updatedAt = None,
      retriedAt = None
    )
  }

  // remove only upload file (with uuid format name)
  def markAsDuplicateDBIO(uuid: UUID)(
      implicit ec: ExecutionContext): DBIOAction[BlobFileState, NoStream, Effect.Write] = {
    findByUuidDBIO(uuid)
      .map(t => (t.state, t.digest))
      .update((BlobFileState.Deleting, None))
      .map(_ => BlobFileState.Deleting)
  }

  private lazy val isDuplicateByDigestCompiled = Compiled {
    (uuid: Rep[UUID], digest: Rep[String]) =>
      table.filter { q =>
        q.digest === digest && q.uuid =!= uuid && q.state === (BlobFileState.Available: BlobFileState)
      }.exists
  }

  // remove upload file if digest is not unique
  def markDBIO(uuid: UUID, digest: String)(
      implicit ec: ExecutionContext): DBIOAction[BlobFileState, NoStream, Effect.All] = {
    isDuplicateByDigestCompiled((uuid, digest.toLowerCase)).result.flatMap { res =>
      if (res) markAsDuplicateDBIO(uuid)
      else {
        for {
          _ <- findByUuidDBIO(uuid).map(_.digest).update(Some(digest))
          _ <- table.filter { q =>
                q.digest === digest && q.uuid =!= uuid && q.state === (BlobFileState.Deleting: BlobFileState)
              }.delete
        } yield BlobFileState.Available
      }
    }
  }

  def findByUuidDBIO(uuid: UUID) = {
    table.filter(_.uuid === uuid)
  }

  lazy val findByUuidCompiled = Compiled { uuid: Rep[UUID] =>
    table.filter(_.uuid === uuid)
  }

  def markOrDestroyDBIO(uuid: UUID)(
      implicit ec: ExecutionContext): DBIOAction[_, NoStream, Effect.All] = {
    findByUuidCompiled(uuid).result.headOption.flatMap {
      case Some(bf) =>
        (bf.digest match {
          case Some(digest) => isDuplicateByDigestCompiled((uuid, digest)).result
          case _            => DBIO.successful(false)
        }).flatMap { isDuplicate =>
          if (isDuplicate) findByUuidDBIO(uuid).delete
          else {
            findByUuidDBIO(uuid).map(_.state).update(BlobFileState.Deleting)
          }
        }
      case _ => DBIO.successful(())
    }
  }

  def markOrDestroyByImageIdDBIO(imageId: Int)(implicit ec: ExecutionContext) = {
    table
      .join(ImageBlobsRepo.table)
      .on(_.uuid === _.id)
      .filter {
        case (t, ibt) =>
          ibt.imageId === imageId &&
            (t.digest.isEmpty ||
                  !t.digest.in(ImageBlobsRepo.duplicateDigestsByImageIdDBIO(imageId)))
      }
      .map(_._1.state)
      .update(BlobFileState.Deleting)
  }

  def destroyOutdatedUploadsDBIO(until: Rep[ZonedDateTime]) = {
    ImageBlobsRepo
      .destroyOutdatedUploadsDBIO(until)
      .join(table)
      .on(_.id === _.uuid)
      .map(_._2.state)
      .update(BlobFileState.Deleting)
  }
}
