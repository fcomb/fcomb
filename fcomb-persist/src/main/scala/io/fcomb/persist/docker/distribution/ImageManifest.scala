package io.fcomb.persist.docker.distribution

import akka.http.scaladsl.util.FastFuture, FastFuture._
import cats.data.Validated
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{ImageManifest ⇒ MImageManifest, Image ⇒ MImage, _}
import io.fcomb.persist._
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

class ImageManifestTable(tag: Tag)
    extends Table[MImageManifest](tag, "dd_image_manifests")
    with PersistTableWithAutoLongPk {
  def sha256Digest = column[String]("sha256_digest")
  def imageId = column[Long]("image_id")
  def tags = column[List[String]]("tags")
  def layersBlobId = column[List[UUID]]("layers_blob_id")
  def schemaVersion = column[Int]("schema_version")
  def schemaV1JsonBlob = column[String]("schema_v1_json_blob")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[Option[ZonedDateTime]]("updated_at")

  // schema v2 details
  def v2ConfigBlobId = column[Option[UUID]]("v2_config_blob_id")
  def v2JsonBlob = column[Option[String]]("v2_json_blob")

  def * =
    (id, sha256Digest, imageId, tags, layersBlobId, schemaVersion, schemaV1JsonBlob,
      createdAt, updatedAt,
      (v2ConfigBlobId, v2JsonBlob)) <>
      ((apply2 _).tupled, unapply2)

  private def apply2(
    id:               Option[Long],
    sha256Digest:     String,
    imageId:          Long,
    tags:             List[String],
    layersBlobId:     List[UUID],
    schemaVersion:    Int,
    schemaV1JsonBlob: String,
    createdAt:        ZonedDateTime,
    updatedAt:        Option[ZonedDateTime],
    v2DetailsTuple:   (Option[UUID], Option[String])
  ) = {
    val schemaV2Details = (schemaVersion, v2DetailsTuple) match {
      case (2, (configBlobId, Some(v2JsonBlob))) ⇒
        Some(ImageManifestSchemaV2Details(configBlobId, v2JsonBlob))
      case _ ⇒ None
    }
    MImageManifest(
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

  def unapply2(m: MImageManifest) = {
    val v2DetailsTuple = m.schemaV2Details match {
      case Some(ImageManifestSchemaV2Details(configBlobId, v2JsonBlob)) ⇒
        (configBlobId, Some(v2JsonBlob))
      case None ⇒ (None, None)
    }
    Some((m.id, m.sha256Digest, m.imageId, m.tags, m.layersBlobId, m.schemaVersion,
      m.schemaV1JsonBlob, m.createdAt, m.updatedAt, v2DetailsTuple))
  }
}

object ImageManifest
    extends PersistModelWithAutoLongPk[MImageManifest, ImageManifestTable] {
  val table = TableQuery[ImageManifestTable]

  private val findByImageIdAndDigestCompiled = Compiled {
    (imageId: Rep[Long], digest: Rep[String]) ⇒
      table
        .filter { q ⇒
          q.imageId === imageId && q.sha256Digest === digest
        }
        .take(1)
  }

  def findByImageIdAndDigest(imageId: Long, digest: String) =
    db.run(findByImageIdAndDigestCompiled((imageId, digest)).result.headOption)

  private val blobsCountIsLessThanExpectedError =
    validationErrorAsFuture("layersBlobId", "Blobs count is less than expected")

  def upsertSchemaV1(
    image:            MImage,
    manifest:         SchemaV1.Manifest,
    schemaV1JsonBlob: String,
    sha256Digest:     String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    findByImageIdAndDigest(image.getId, sha256Digest).flatMap {
      case Some(im) ⇒ FastFuture.successful(Validated.valid(im))
      case None ⇒
        val digests = manifest.fsLayers
          .map(_.parseDigest)
          .filterNot(_ == MImageManifest.emptyTarSha256Digest)
          .distinct
        val tags =
          if (manifest.tag.nonEmpty) List(manifest.tag)
          else Nil
        ImageBlob.findIdsByImageIdAndDigests(image.getId, digests).flatMap { blobIds ⇒
          if (blobIds.length != digests.length) blobsCountIsLessThanExpectedError
          else create(MImageManifest(
            sha256Digest = sha256Digest,
            imageId = image.getId,
            tags = tags,
            layersBlobId = blobIds.toList,
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
    image:            MImage,
    manifest:         SchemaV2.Manifest,
    reference:        String,
    configBlob:       ImageBlob,
    schemaV1JsonBlob: String,
    schemaV2JsonBlob: String,
    sha256Digest:     String
  )(implicit ec: ExecutionContext): Future[ValidationModel] = {
    findByImageIdAndDigest(image.getId, sha256Digest).flatMap {
      case Some(im) ⇒
        updateTagsByReference(im, reference)
          .fast
          .map(_ ⇒ Validated.valid(im))
      case None ⇒
        val digests = manifest.layers
          .map(_.parseDigest)
          .filterNot(_ == MImageManifest.emptyTarSha256Digest)
          .distinct
        ImageBlob.findIdsByImageIdAndDigests(image.getId, digests).flatMap { blobIds ⇒
          if (blobIds.length != digests.length) blobsCountIsLessThanExpectedError
          else {
            val schemaV2Details = ImageManifestSchemaV2Details(
              configBlobId = configBlob.id,
              jsonBlob = schemaV2JsonBlob
            )
            val tags =
              if (reference.nonEmpty && !reference.startsWith(MImageManifest.sha256Prefix))
                List(reference)
              else Nil
            create(MImageManifest(
              sha256Digest = sha256Digest,
              imageId = image.getId,
              tags = tags,
              layersBlobId = blobIds.toList,
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

  def updateTagsByReference(im: MImageManifest, reference: String)(
    implicit
    ec: ExecutionContext
  ): Future[Unit] = {
    val tags =
      if (reference.nonEmpty && !reference.startsWith(MImageManifest.sha256Prefix) &&
        !im.tags.contains(reference)) List(reference)
      else Nil
    if (tags.nonEmpty) ImageManifestTag.upsertTags(im.imageId, im.getId, tags)
    else FastFuture.successful(())
  }

  override def create(manifest: MImageManifest)(
    implicit
    ec: ExecutionContext,
    m:  Manifest[MImageManifest]
  ): Future[ValidationModel] = {
    runInTransaction(TransactionIsolation.Serializable)(
      createWithValidationDBIO(manifest).flatMap {
        case res @ Validated.Valid(im) ⇒
          for {
            _ ← ImageManifestLayer.insertLayersDBIO(im.getId, im.layersBlobId)
            _ ← ImageManifestTag.upsertTagsDBIO(im.imageId, im.getId, im.tags)
          } yield res
        case res ⇒ DBIO.successful(res)
      }
    )
  }

  def findByImageIdAndReferenceAsManifestV2(imageId: Long, reference: String)(
    implicit
    ec: ExecutionContext
  ): Future[Option[SchemaManifest]] = {
    // TODO: find by tags
    // for {
    //   Some(manifest) ← db.run(table.filter { q ⇒
    //     q.imageId === imageId &&
    //       q.sha256Digest === reference.drop(MImageManifest.sha256Prefix.length)
    //   }.result.headOption)
    //   blobs ← ImageBlob.findByIds(manifest.configBlobId :: manifest.layersBlobId)
    // } yield {
    //   val blobsMap = blobs.map(b ⇒ (b.getId, b)).toMap

    //   def descriptorByUuid(uuid: UUID) = {
    //     val blob = blobsMap(uuid)
    //     SchemaV2.Descriptor(
    //       mediaType = Some(blob.contentType),
    //       size = blob.length,
    //       digest = blob.sha256Digest.get
    //     )
    //   }

    //   Some(SchemaV2.Manifest(
    //     config = descriptorByUuid(manifest.configBlobId),
    //     layers = manifest.layersBlobId.map(descriptorByUuid)
    //   ))
    // }
    ???
  }

  private val findIdAndTagsByImageIdAndTagCompiled = Compiled {
    (imageId: Rep[Long], tag: Rep[String]) ⇒
      table.filter { q ⇒
        q.imageId === imageId && tag === q.tags.any
      }.map(m ⇒ (m.pk, m.tags))
  }

  private val findTagsByImageIdCompiled = Compiled {
    (imageId: Rep[Long], limit: ConstColumn[Long], id: Rep[Long],
    offset: ConstColumn[Long]) ⇒
      table.filter { q ⇒
        q.imageId === imageId && q.pk >= id
      }.sortBy(_.id.asc).map(_.tags.unnest).drop(offset).take(limit)
  }

  val fetchLimit = 256

  def findTagsByImageId(imageId: Long, n: Option[Int], last: Option[String])(
    implicit
    ec: ExecutionContext
  ): Future[(Seq[String], Int, Boolean)] = {
    val limit = n match {
      case Some(v) if v > 0 && v <= fetchLimit ⇒ v
      case _                                   ⇒ fetchLimit
    }
    val since = last match {
      case Some(tag) ⇒
        findIdAndTagsByImageIdAndTagCompiled((imageId, tag)).result.headOption.map {
          case Some((id, tags)) ⇒ (id, tags.indexOf(tag) + 1L)
          case None             ⇒ (0L, 0L)
        }
      case None ⇒ DBIO.successful((0L, 0L))
    }
    db.run(for {
      (id, offset) ← since
      tags ← findTagsByImageIdCompiled((imageId, limit + 1L, id, offset)).result
    } yield tags)
      .fast
      .map { tags ⇒
        (tags.take(limit), limit, tags.length > limit)
      }
  }

  def destroy(imageId: Long, digest: String) =
    db.run(table.filter { q ⇒
      q.imageId === imageId && q.sha256Digest === digest
    }.delete)
}
