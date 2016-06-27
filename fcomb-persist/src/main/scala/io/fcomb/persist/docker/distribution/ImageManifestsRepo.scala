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

import akka.http.scaladsl.util.FastFuture, FastFuture._
import cats.data.Validated
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution._
import io.fcomb.persist._
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

class ImageManifestTable(tag: Tag)
    extends Table[ImageManifest](tag, "dd_image_manifests")
    with PersistTableWithAutoIntPk {
  def sha256Digest     = column[String]("sha256_digest")
  def imageId          = column[Int]("image_id")
  def tags             = column[List[String]]("tags")
  def layersBlobId     = column[List[UUID]]("layers_blob_id")
  def schemaVersion    = column[Int]("schema_version")
  def schemaV1JsonBlob = column[String]("schema_v1_json_blob")
  def createdAt        = column[ZonedDateTime]("created_at")
  def updatedAt        = column[Option[ZonedDateTime]]("updated_at")

  // schema v2 details
  def v2ConfigBlobId = column[Option[UUID]]("v2_config_blob_id")
  def v2JsonBlob     = column[Option[String]]("v2_json_blob")

  def * =
    (id,
     sha256Digest,
     imageId,
     tags,
     layersBlobId,
     schemaVersion,
     schemaV1JsonBlob,
     createdAt,
     updatedAt,
     (v2ConfigBlobId, v2JsonBlob)) <>
      ((apply2 _).tupled, unapply2)

  private def apply2(
      id: Option[Int],
      sha256Digest: String,
      imageId: Int,
      tags: List[String],
      layersBlobId: List[UUID],
      schemaVersion: Int,
      schemaV1JsonBlob: String,
      createdAt: ZonedDateTime,
      updatedAt: Option[ZonedDateTime],
      v2DetailsTuple: (Option[UUID], Option[String])
  ) = {
    val schemaV2Details = (schemaVersion, v2DetailsTuple) match {
      case (2, (configBlobId, Some(v2JsonBlob))) =>
        Some(ImageManifestSchemaV2Details(configBlobId, v2JsonBlob))
      case _ => None
    }
    ImageManifest(
      id = id,
      sha256Digest = sha256Digest,
      imageId = imageId,
      tags = tags,
      layersBlobId = layersBlobId,
      schemaVersion = schemaVersion,
      schemaV1JsonBlob = schemaV1JsonBlob,
      schemaV2Details = schemaV2Details,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
  }

  def unapply2(m: ImageManifest) = {
    val v2DetailsTuple = m.schemaV2Details match {
      case Some(ImageManifestSchemaV2Details(configBlobId, v2JsonBlob)) =>
        (configBlobId, Some(v2JsonBlob))
      case None => (None, None)
    }
    Some(
      (m.id,
       m.sha256Digest,
       m.imageId,
       m.tags,
       m.layersBlobId,
       m.schemaVersion,
       m.schemaV1JsonBlob,
       m.createdAt,
       m.updatedAt,
       v2DetailsTuple))
  }
}

object ImageManifestsRepo extends PersistModelWithAutoIntPk[ImageManifest, ImageManifestTable] {
  val table = TableQuery[ImageManifestTable]

  private lazy val findByImageIdAndDigestCompiled = Compiled {
    (imageId: Rep[Int], digest: Rep[String]) =>
      table.filter { q =>
        q.imageId === imageId && q.sha256Digest === digest
      }.take(1)
  }

  def findByImageIdAndDigest(imageId: Int, digest: String) =
    db.run(findByImageIdAndDigestCompiled((imageId, digest)).result.headOption)

  private def blobsCountIsLessThanExpected(
      blobs: Seq[(UUID, Option[String])],
      digests: Set[String]
  ) = {
    val existingDigests = blobs.collect { case (_, Some(dgst)) => dgst }.toSet
    val notFound = digests
      .filterNot(existingDigests.contains)
      .map(dgst => s"${ImageManifest.sha256Prefix}$dgst")
      .mkString(", ")
    validationErrorAsFuture("layersBlobId", s"Unknown blobs: $notFound")
  }

  def upsertSchemaV1(
      image: Image,
      manifest: SchemaV1.Manifest,
      schemaV1JsonBlob: String,
      sha256Digest: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    findByImageIdAndDigest(image.getId, sha256Digest).flatMap {
      case Some(im) => FastFuture.successful(Validated.valid(im))
      case None =>
        val digests = manifest.fsLayers.map(_.getDigest).toSet
        val tags =
          if (manifest.tag.nonEmpty) List(manifest.tag)
          else Nil
        ImageBlobsRepo.findIdsWithDigestByImageIdAndDigests(image.getId, digests).flatMap {
          blobs =>
            if (blobs.length != digests.size) blobsCountIsLessThanExpected(blobs, digests)
            else
              create(
                ImageManifest(
                  id = None,
                  sha256Digest = sha256Digest,
                  imageId = image.getId,
                  tags = tags,
                  layersBlobId = blobs.map(_._1).toList,
                  schemaVersion = 1,
                  schemaV1JsonBlob = schemaV1JsonBlob,
                  schemaV2Details = None,
                  createdAt = ZonedDateTime.now,
                  updatedAt = None
                ))
        }
    }
  }

  def upsertSchemaV2(
      image: Image,
      manifest: SchemaV2.Manifest,
      reference: Reference,
      configBlob: ImageBlob,
      schemaV1JsonBlob: String,
      schemaV2JsonBlob: String,
      sha256Digest: String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    findByImageIdAndDigest(image.getId, sha256Digest).flatMap {
      case Some(im) =>
        updateTagsByReference(im, reference).fast.map(_ => Validated.valid(im))
      case None =>
        val digests = manifest.layers.map(_.getDigest).toSet
        val emptyTarResFut =
          if (digests.contains(ImageManifest.emptyTarSha256Digest))
            ImageBlobsRepo.createEmptyTarIfNotExists(image.getId)
          else FastFuture.successful(())
        (for {
          _       <- emptyTarResFut
          blobIds <- ImageBlobsRepo.findIdsWithDigestByImageIdAndDigests(image.getId, digests)
        } yield blobIds).flatMap { blobs =>
          if (blobs.length != digests.size) blobsCountIsLessThanExpected(blobs, digests)
          else {
            val schemaV2Details = ImageManifestSchemaV2Details(
              configBlobId = configBlob.id,
              jsonBlob = schemaV2JsonBlob
            )
            val tags = reference match {
              case Reference.Tag(tag) => List(tag)
              case _                  => Nil
            }
            create(
              ImageManifest(
                id = None,
                sha256Digest = sha256Digest,
                imageId = image.getId,
                tags = tags,
                layersBlobId = blobs.map(_._1).toList,
                schemaVersion = 2,
                schemaV1JsonBlob = schemaV1JsonBlob,
                schemaV2Details = Some(schemaV2Details),
                createdAt = ZonedDateTime.now,
                updatedAt = None
              ))
          }
        }
    }
  }

  def updateTagsByReference(im: ImageManifest, reference: Reference)(
      implicit ec: ExecutionContext
  ): Future[Unit] = {
    val tags = reference match {
      case Reference.Tag(tag) if !im.tags.contains(tag) => List(tag)
      case _                                            => Nil
    }
    if (tags.nonEmpty)
      runInTransaction(TransactionIsolation.Serializable)(for {
        _ <- ImageManifestTagsRepo.upsertTagsDBIO(im.imageId, im.getId, tags)
        _ <- sqlu"""
          UPDATE #${ImageManifestsRepo.table.baseTableRow.tableName}
            SET tags = tags || ${reference.value},
                updated_at = ${ZonedDateTime.now()}
            WHERE id = ${im.getId}
          """
      } yield ())
    else FastFuture.successful(())
  }

  override def create(manifest: ImageManifest)(
      implicit ec: ExecutionContext,
      m: Manifest[ImageManifest]
  ): Future[ValidationModel] = {
    runInTransaction(TransactionIsolation.Serializable)(
      createWithValidationDBIO(manifest).flatMap {
        case res @ Validated.Valid(im) =>
          for {
            _ <- ImageManifestLayersRepo.insertLayersDBIO(im.getId, im.layersBlobId)
            _ <- ImageManifestTagsRepo.upsertTagsDBIO(im.imageId, im.getId, im.tags)
          } yield res
        case res => DBIO.successful(res)
      }
    )
  }

  def findByImageIdAndReference(imageId: Int, reference: Reference)(
      implicit ec: ExecutionContext
  ): Future[Option[ImageManifest]] = reference match {
    case Reference.Digest(dgst) => findByImageIdAndDigest(imageId, dgst)
    case Reference.Tag(tag)     => findByImageIdAndTag(imageId, tag)
  }

  private lazy val findByImageIdAndTagCompiled = Compiled {
    (imageId: Rep[Int], tag: Rep[String]) =>
      table
        .join(ImageManifestTagsRepo.table)
        .on(_.id === _.imageManifestId)
        .filter(_._2.tag === tag)
        .map(_._1)
  }

  def findByImageIdAndTag(imageId: Int, tag: String): Future[Option[ImageManifest]] =
    db.run(findByImageIdAndTagCompiled((imageId, tag)).result.headOption)

  private lazy val findIdAndTagsByImageIdAndTagCompiled = Compiled {
    (imageId: Rep[Int], tag: Rep[String]) =>
      table.filter { q =>
        q.imageId === imageId && tag === q.tags.any
      }.map(m => (m.pk, m.tags))
  }

  private lazy val findTagsByImageIdCompiled = Compiled {
    (imageId: Rep[Int], limit: ConstColumn[Long], id: Rep[Int], offset: ConstColumn[Long]) =>
      table.filter { q =>
        q.imageId === imageId && q.pk >= id
      }.sortBy(_.id.asc).map(_.tags.unnest).drop(offset).take(limit)
  }

  val fetchLimit = 256

  def findTagsByImageId(imageId: Int, n: Option[Int], last: Option[String])(
      implicit ec: ExecutionContext
  ): Future[(Seq[String], Int, Boolean)] = {
    val limit = n match {
      case Some(v) if v > 0 && v <= fetchLimit => v
      case _                                   => fetchLimit
    }
    val since = last match {
      case Some(tag) =>
        findIdAndTagsByImageIdAndTagCompiled((imageId, tag)).result.headOption.map {
          case Some((id, tags)) => (id, tags.indexOf(tag) + 1L)
          case None             => (0, 0L)
        }
      case None => DBIO.successful((0, 0L))
    }
    db.run(for {
        (id, offset) <- since
        tags         <- findTagsByImageIdCompiled((imageId, limit + 1L, id, offset)).result
      } yield tags)
      .fast
      .map { tags =>
        (tags.take(limit), limit, tags.length > limit)
      }
  }

  def destroy(imageId: Int, digest: String)(implicit ec: ExecutionContext): Future[Boolean] =
    db.run {
      table.filter { q =>
        q.imageId === imageId && q.sha256Digest === digest
      }.delete.map(_ != 0)
    }
}
