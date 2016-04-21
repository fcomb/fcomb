package io.fcomb.persist.docker.distribution

import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{Manifest ⇒ MManifest, ImageManifest ⇒ MImageManifest, _}
import io.fcomb.persist._
import io.fcomb.validations._
import scala.concurrent.{ExecutionContext, Future}
import scalaz._, Scalaz._
import org.apache.commons.codec.digest.DigestUtils
import akka.http.scaladsl.util.FastFuture, FastFuture._
import java.time.ZonedDateTime
import java.util.UUID

class ImageManifestTable(tag: Tag) extends Table[MImageManifest](tag, "docker_distribution_image_manifests") with PersistTableWithAutoLongPk {
  def sha256Digest = column[String]("sha256_digest")
  def imageId = column[Long]("image_id")
  def tags = column[List[String]]("tags")
  def configBlobId = column[UUID]("config_blob_id")
  def layersBlobId = column[List[UUID]]("layers_blob_id")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")

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

  def upsertByRequest(name: String, reference: String, manifest: MManifest, rawManifest: String)(
    implicit
    ec: ExecutionContext
  ) = {
    val sha256Digest = DigestUtils.sha256Hex(rawManifest)
    val mm = manifest match {
      case m: ManifestV1 ⇒ ???
      case m: ManifestV2 ⇒ m
    }
    val digests = (mm.config.digest :: mm.layers.map(_.digest)).map(_.drop(sha256Prefix.length))
      .distinct
    (for {
      Some(imageId) ← Image.findIdByName(name) // TODO
      blobs ← ImageBlob.findByImageIdAndDigests(imageId, digests)
      manifestIdOpt ← findIdByImageIdAndDigest(imageId, sha256Digest)
    } yield (imageId, blobs, manifestIdOpt)).flatMap {
      case (imageId, blobs, manifestIdOpt) ⇒
        val blobsMap = blobs.map(b ⇒ (s"$sha256Prefix${b.sha256Digest.get}", b.getId)).toMap
        assert(blobsMap.length === digests.length) // TODO
        val timeNow = ZonedDateTime.now
        val configBlobId = blobsMap(mm.config.digest)
        val layersBlobId = mm.layers.map(l ⇒ blobsMap(l.digest))
        manifestIdOpt match {
          case Some(manifestId) ⇒
            update(manifestId)(_.copy(
              configBlobId = configBlobId,
              layersBlobId = layersBlobId,
              updatedAt = timeNow
            ))
          case None ⇒
            create(MImageManifest(
              sha256Digest = sha256Digest,
              imageId = imageId,
              tags = List.empty, // TODO
              configBlobId = configBlobId,
              layersBlobId = layersBlobId,
              createdAt = timeNow,
              updatedAt = timeNow
            ))
        }
    }
  }

  def findByImageIdAndReferenceAsManifestV2(imageId: Long, reference: String)(
    implicit
    ec: ExecutionContext
  ) = {
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
        Descriptor(
          mediaType = "application/vnd.docker.image.rootfs.diff.tar.gzip", // TODO: .contentType
          size = blob.length,
          digest = blob.sha256Digest.get
        )
      }

      Some(ManifestV2(
        config = descriptorByUuid(manifest.configBlobId),
        layers = manifest.layersBlobId.map(descriptorByUuid)
      ))
    }
  }

  private val findTagsByImageIdCompiled = Compiled { imageId: Rep[Long] =>
    table
      .filter(_.imageId === imageId)
      .map(_.tags)
  }

  def findTagsByImageId(imageId: Long)(
    implicit ec: ExecutionContext
  ): Future[Seq[String]] =
    db.run(findTagsByImageIdCompiled(imageId).result).fast.map(_.flatten)

  def destroy(imageId: Long, digest: String) =
    db.run(table.filter { q ⇒
      q.imageId === imageId && q.sha256Digest === digest
    }.delete)
}
