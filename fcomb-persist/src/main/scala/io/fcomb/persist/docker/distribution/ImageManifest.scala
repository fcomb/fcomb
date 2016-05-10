package io.fcomb.persist.docker.distribution

import akka.http.scaladsl.util.FastFuture, FastFuture._
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{ImageManifest ⇒ MImageManifest, _}
import io.fcomb.persist._
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ImageManifestTable(tag: Tag) extends Table[MImageManifest](tag, "docker_distribution_image_manifests") with PersistTableWithAutoLongPk {
  def sha256Digest = column[String]("sha256_digest")
  def imageId = column[Long]("image_id")
  def tags = column[List[String]]("tags")
  def configBlobId = column[UUID]("config_blob_id")
  def layersBlobId = column[List[UUID]]("layers_blob_id")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, sha256Digest, imageId, tags, configBlobId, layersBlobId, createdAt, updatedAt) <>
      ((MImageManifest.apply _).tupled, MImageManifest.unapply)
}

object ImageManifest extends PersistModelWithAutoLongPk[MImageManifest, ImageManifestTable] {
  val table = TableQuery[ImageManifestTable]

  private val sha256Prefix = "sha256:"

  private val findIdByImageIdAndDigestCompiled = Compiled {
    (imageId: Rep[Long], digest: Rep[String]) ⇒
      table
        .filter { q ⇒
          q.imageId === imageId && q.sha256Digest === digest
        }
        .map(_.pk)
        .take(1)
  }

  def findIdByImageIdAndDigest(imageId: Long, digest: String) =
    db.run(findIdByImageIdAndDigestCompiled((imageId, digest)).result.headOption)

  def upsertByRequest(name: String, reference: String, manifest: SchemaManifest, sha256Digest: String)(
    implicit
    ec: ExecutionContext
  ) = {
    val mm = manifest match {
      case m: SchemaV1.Manifest ⇒
        println(m)
        ???
      case m: SchemaV2.Manifest ⇒ m
    }
    val digests = (mm.config.digest :: mm.layers.map(_.digest)).map(_.drop(sha256Prefix.length))
      .distinct
    val res = (for {
      Some(imageId) ← Image.findIdByName(name) // TODO
      blobs ← ImageBlob.findByImageIdAndDigests(imageId, digests)
      manifestIdOpt ← findIdByImageIdAndDigest(imageId, sha256Digest)
    } yield (imageId, blobs, manifestIdOpt)).flatMap {
      case (imageId, blobs, manifestIdOpt) ⇒
        val blobsMap = blobs.map(b ⇒ (s"$sha256Prefix${b.sha256Digest.get}", b.getId)).toMap
        assert(blobsMap.size == digests.length) // TODO
        val configBlobId = blobsMap(mm.config.digest)
        val layersBlobId = mm.layers.map(l ⇒ blobsMap(l.digest))
        manifestIdOpt match {
          case Some(manifestId) ⇒
            update(manifestId)(_.copy(
              configBlobId = configBlobId,
              layersBlobId = layersBlobId,
              updatedAt = Some(ZonedDateTime.now)
            ))
          case None ⇒
            create(MImageManifest(
              sha256Digest = sha256Digest,
              imageId = imageId,
              tags = List.empty, // TODO
              configBlobId = configBlobId,
              layersBlobId = layersBlobId,
              createdAt = ZonedDateTime.now,
              updatedAt = None
            ))
        }
    }
    res.onComplete {
      case scala.util.Success(_) ⇒ println("ok")
      case scala.util.Failure(e) ⇒
        println(e)
        e.printStackTrace()
    }
    res
  }

  def findByImageIdAndReferenceAsManifestV2(imageId: Long, reference: String)(
    implicit
    ec: ExecutionContext
  ): Future[Option[SchemaManifest]] = {
    // TODO: find by tags
    for {
      Some(manifest) ← db.run(table.filter { q ⇒
        q.imageId === imageId && q.sha256Digest === reference.drop(sha256Prefix.length)
      }.result.headOption)
      blobs ← ImageBlob.findByIds(manifest.configBlobId :: manifest.layersBlobId)
    } yield {
      val blobsMap = blobs.map(b ⇒ (b.getId, b)).toMap

      def descriptorByUuid(uuid: UUID) = {
        val blob = blobsMap(uuid)
        SchemaV2.Descriptor(
          mediaType = Some(blob.contentType),
          size = blob.length,
          digest = blob.sha256Digest.get
        )
      }

      Some(SchemaV2.Manifest(
        config = descriptorByUuid(manifest.configBlobId),
        layers = manifest.layersBlobId.map(descriptorByUuid)
      ))
    }
  }

  private val findIdAndTagsByImageIdAndTagCompiled = Compiled {
    (imageId: Rep[Long], tag: Rep[String]) ⇒
      table
        .filter { q ⇒
          q.imageId === imageId && tag === q.tags.any
        }
        .map(m ⇒ (m.pk, m.tags))
  }

  private val findTagsByImageIdCompiled = Compiled {
    (imageId: Rep[Long], limit: ConstColumn[Long], id: Rep[Long], offset: ConstColumn[Long]) ⇒
      table
        .filter { q ⇒
          q.imageId === imageId && q.pk >= id
        }
        .sortBy(_.id.asc)
        .map(_.tags.unnest)
        .drop(offset)
        .take(limit)
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
    } yield tags).fast.map { tags ⇒
      (tags.take(limit), limit, tags.length > limit)
    }
  }

  def destroy(imageId: Long, digest: String) =
    db.run(table.filter { q ⇒
      q.imageId === imageId && q.sha256Digest === digest
    }.delete)
}
