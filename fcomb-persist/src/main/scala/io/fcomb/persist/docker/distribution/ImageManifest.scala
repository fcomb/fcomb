package io.fcomb.persist.docker.distribution

import akka.http.scaladsl.util.FastFuture, FastFuture._
import io.circe.Json
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{ImageManifest ⇒ MImageManifest, _}, MImageManifest.sha256Prefix
import io.fcomb.persist._
import java.time.ZonedDateTime
import java.util.UUID
import org.apache.commons.codec.digest.DigestUtils
import scala.concurrent.{ExecutionContext, Future}

class ImageManifestTable(tag: Tag) extends Table[MImageManifest](tag, "docker_distribution_image_manifests") with PersistTableWithAutoLongPk {
  def sha256Digest = column[String]("sha256_digest")
  def imageId = column[Long]("image_id")
  def tags = column[List[String]]("tags")
  def layersBlobId = column[List[UUID]]("layers_blob_id")
  def schemaVersion = column[Int]("schema_version")
  def schemaV1JsonBlob = column[Json]("schema_v1_json_blob")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[Option[ZonedDateTime]]("updated_at")

  // schema v2 details
  def v2ConfigBlobId = column[Option[UUID]]("v2_config_blob_id")
  def v2JsonBlob = column[Option[Json]]("v2_json_blob")

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
    schemaV1JsonBlob: Json,
    createdAt:        ZonedDateTime,
    updatedAt:        Option[ZonedDateTime],
    v2DetailsTuple:   (Option[UUID], Option[Json])
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
      m.schemaV1JsonBlob, m.createdAt, m.updatedAt,
      v2DetailsTuple))
  }
}

object ImageManifest extends PersistModelWithAutoLongPk[MImageManifest, ImageManifestTable] {
  val table = TableQuery[ImageManifestTable]

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

  def upsertByRequest(
    name:         String,
    reference:    String,
    manifest:     SchemaManifest,
    sha256Digest: String
  )(
    implicit
    ec:  ExecutionContext,
    mat: akka.stream.Materializer
  ): Future[ValidationModel] = {
    val mm = manifest match {
      case m: SchemaV1.Manifest ⇒
        println(m)
        import io.circe._, io.circe.syntax._, io.circe.generic.auto._
        import io.fcomb.json.docker.distribution.Formats._
        println(m.asJson)
        ???
      case m: SchemaV2.Manifest ⇒ m
    }
    val res = (for {
      Some(imageId) ← Image.findIdByName(name) // TODO
      manifestIdOpt ← findIdByImageIdAndDigest(imageId, sha256Digest)
    } yield (imageId, manifestIdOpt)).flatMap {
      case (imageId, manifestIdOpt) ⇒
        manifestIdOpt match {
          case Some(manifestId) ⇒
            // TODO: update tags or do nothing
            update(manifestId)(_.copy(
              updatedAt = Some(ZonedDateTime.now)
            ))
          case None ⇒
            val digests = (mm.config.digest :: mm.layers.map(_.digest))
              .map(_.drop(sha256Prefix.length))
              .filterNot(_ == MImageManifest.emptyTarSha256Digest)
              .distinct
            val configDigest = digests.head
            val configFile = new java.io.File(s"${io.fcomb.utils.Config.docker.distribution.imageStorage}/blobs/${configDigest.take(2)}/$configDigest") // TODO
            import io.fcomb.utils.StringUtils
            import io.fcomb.utils.Units._
            import io.circe.syntax._
            import io.fcomb.json.docker.distribution.Formats._
            import akka.stream.scaladsl._
            (for {
              blobs ← ImageBlob.findByImageIdAndDigests(imageId, digests)
              Some(configBlob) = blobs.find(_.sha256Digest.contains(configDigest))
              _ = assert(configBlob.length <= 1.MB)
              imageConfigJson ← FileIO.fromFile(configFile).map(_.utf8String).runWith(Sink.head)
              imageConfig = io.circe.parser.decode[SchemaV2.ImageConfig](imageConfigJson)
              config = io.circe.parser.decode[SchemaV1.Config](imageConfigJson)(decodeSchemaV1Config)
            } yield (blobs, imageConfigJson, imageConfig, config)).flatMap {
              case (blobs, imageConfigJson, cats.data.Xor.Right(imageConfig), cats.data.Xor.Right(config)) ⇒
                val blobsMap = blobs
                  .map(b ⇒ (s"$sha256Prefix${b.sha256Digest.getOrElse("")}", b.getId))
                  .toMap
                assert(blobsMap.size == digests.length) // TODO
                assert(imageConfig.history.nonEmpty)
                assert(imageConfig.rootFs.diffIds.nonEmpty)
                val layersBlobId = blobsMap.values.toList
                val schemaV2Details = ImageManifestSchemaV2Details(
                  configBlobId = blobsMap.get(configDigest),
                  jsonBlob = Json.Null
                )

                val baseLayerId = imageConfig.rootFs.baseLayer.map(DigestUtils.sha384Hex(_).take(32))
                val (lastParentId, remainLayers, history, fsLayers) =
                  imageConfig.history.init.foldLeft(("", mm.layers, List.empty[SchemaV1.Layer], List.empty[SchemaV1.FsLayer])) {
                    case ((parentId, layers, historyList, fsLayersList), img) ⇒
                      val (blobSum, layersTail) =
                        if (img.isEmptyLayer) (MImageManifest.emptyTarSha256DigestFull, layers)
                        else (layers.head.digest.drop(sha256Prefix.length), layers.tail) // TODO
                      val v1Id = DigestUtils.sha256Hex(s"$blobSum $parentId")
                      val createdBy = img.createdBy.map(List(_)).getOrElse(Nil)
                      val throwAway = if (img.isEmptyLayer) Some(true) else None
                      val historyLayer = SchemaV1.Layer(
                        id = v1Id,
                        parent = StringUtils.trim(Some(parentId)),
                        comment = img.comment,
                        created = Some(img.created),
                        containerConfig = Some(SchemaV1.LayerContainerConfig(createdBy)),
                        author = img.author,
                        throwAway = throwAway
                      )
                      val fsLayer = SchemaV1.FsLayer(s"$sha256Prefix$blobSum")
                      val currentId =
                        if (parentId.isEmpty) baseLayerId.getOrElse(v1Id)
                        else v1Id
                      (currentId, layersTail, historyLayer :: historyList, fsLayer :: fsLayersList)
                  }

                val (configHistory, configFsLayer) = {
                  val isEmptyLayer = imageConfig.history.last.isEmptyLayer
                  val blobSum =
                    if (isEmptyLayer) MImageManifest.emptyTarSha256DigestFull
                    else remainLayers.headOption.map(_.digest.drop(sha256Prefix.length)).getOrElse("")
                  val v1Id = DigestUtils.sha256Hex(s"$blobSum $lastParentId $imageConfigJson")
                  val parent =
                    if (lastParentId.isEmpty) config.parent
                    else Some(lastParentId)
                  val throwAway = if (isEmptyLayer) Some(true) else None
                  val historyLayer = config.copy(
                    id = Some(v1Id),
                    parent = parent,
                    throwAway = throwAway
                  )
                  val fsLayer = SchemaV1.FsLayer(s"$sha256Prefix$blobSum")
                  (historyLayer, fsLayer)
                }

                val schemaV1JsonBlob = SchemaV1.Manifest(
                  name = name,
                  tag = "",
                  fsLayers = configFsLayer :: fsLayers,
                  architecture = imageConfig.architecture,
                  history = configHistory :: history,
                  signatures = Nil
                ).asJson

                println("schemaV1JsonBlob:")
                println(schemaV1JsonBlob.asJson)

                ???
                create(MImageManifest(
                  sha256Digest = sha256Digest,
                  imageId = imageId,
                  tags = List.empty, // TODO
                  layersBlobId = layersBlobId,
                  schemaVersion = 2,
                  schemaV1JsonBlob = schemaV1JsonBlob,
                  schemaV2Details = Some(schemaV2Details),
                  createdAt = ZonedDateTime.now,
                  updatedAt = None
                ))
            }
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
