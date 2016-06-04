package io.fcomb.docker.distribution.manifest

import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import cats.data.{Validated, Xor}
import io.fcomb.models.docker.distribution.SchemaV2.{Manifest ⇒ ManifestV2}
import io.fcomb.models.docker.distribution.{Reference, Image ⇒ Image}
import io.fcomb.models.docker.distribution.ImageManifest.sha256Prefix
import io.fcomb.models.errors.docker.distribution.DistributionError, DistributionError._
import io.fcomb.persist.docker.distribution.{ImageManifestsRepo, ImageBlobsRepo}
import io.fcomb.docker.distribution.utils.BlobFile
import io.fcomb.utils.Units._
import org.apache.commons.codec.digest.DigestUtils
import scala.concurrent.{ExecutionContext, Future}

object SchemaV2 {
  def upsertAsImageManifest(
    image:       Image,
    reference:   Reference,
    manifest:    ManifestV2,
    rawManifest: String
  )(implicit ec: ExecutionContext, mat: Materializer): Future[Xor[DistributionError, String]] = {
    val sha256Digest = DigestUtils.sha256Hex(rawManifest)
    ImageManifestsRepo.findByImageIdAndDigest(image.getId, sha256Digest).flatMap {
      case Some(im) ⇒
        ImageManifestsRepo.updateTagsByReference(im, reference)
          .fast
          .map(_ ⇒ Xor.right(im.sha256Digest))
      case None ⇒
        val configDigest = manifest.config.getDigest
        ImageBlobsRepo.findByImageIdAndDigest(image.getId, configDigest).flatMap {
          case Some(configBlob) ⇒
            if (configBlob.length > 1.MB)
              FastFuture.successful(unknowError("Config JSON size is more than 1 MB"))
            else {
              val configFile = BlobFile.getBlobFilePath(configDigest)
              getImageConfig(configFile).flatMap { imageConfig ⇒
                SchemaV1.convertFromSchemaV2(image, manifest, imageConfig) match {
                  case Xor.Right(schemaV1JsonBlob) ⇒
                    ImageManifestsRepo.upsertSchemaV2(image, manifest, reference, configBlob,
                      schemaV1JsonBlob, rawManifest, sha256Digest)
                      .fast
                      .map {
                        case Validated.Valid(_)   ⇒ Xor.right(sha256Digest)
                        case Validated.Invalid(e) ⇒ unknowError(e.map(_.message).mkString(";"))
                      }
                  case Xor.Left(e) ⇒ FastFuture.successful(unknowError(e))
                }
              }
            }
          case None ⇒
            FastFuture.successful(unknowError(s"Config blob `$sha256Prefix$configDigest` not found"))
        }
    }
  }

  @inline
  private def unknowError(msg: String) =
    Xor.left[DistributionError, String](Unknown(msg))

  private def getImageConfig(configFile: java.io.File)(implicit mat: Materializer) =
    FileIO.fromPath(configFile.toPath).map(_.utf8String).runWith(Sink.head)
}
