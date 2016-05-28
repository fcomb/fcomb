package io.fcomb.docker.distribution.manifest

import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import cats.data.{Validated, Xor}
import io.fcomb.models.docker.distribution.SchemaV2.{Manifest ⇒ ManifestV2}
import io.fcomb.models.docker.distribution.{Reference, Image ⇒ MImage}
import io.fcomb.models.errors.docker.distribution.DistributionError, DistributionError._
import io.fcomb.persist.docker.distribution.{ImageManifest ⇒ PImageManifest, ImageBlob ⇒ PImageBlob}
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.utils.Units._
import org.apache.commons.codec.digest.DigestUtils
import scala.concurrent.{ExecutionContext, Future}

object SchemaV2 {
  def upsertAsImageManifest(
    image:       MImage,
    reference:   Reference,
    manifest:    ManifestV2,
    rawManifest: String
  )(implicit ec: ExecutionContext, mat: Materializer): Future[Xor[DistributionError, String]] = {
    val sha256Digest = DigestUtils.sha256Hex(rawManifest)
    PImageManifest.findByImageIdAndDigest(image.getId, sha256Digest).flatMap {
      case Some(im) ⇒
        PImageManifest.updateTagsByReference(im, reference)
          .fast
          .map(_ ⇒ Xor.right(im.sha256Digest))
      case None ⇒
        val configDigest = manifest.config.getDigest
        (for {
          Some(configBlob) ← PImageBlob.findByImageIdAndDigest(image.getId, configDigest)
          _ = assert(configBlob.length <= 1.MB, "Config JSON size is more than 1 MB")
          configFile = BlobFile.blobFile(configDigest)
          imageConfig ← getImageConfig(configFile)
        } yield (configBlob, imageConfig)).flatMap {
          case (configBlob, imageConfig) ⇒
            SchemaV1.convertFromSchemaV2(image, manifest, imageConfig) match {
              case Xor.Right(schemaV1Manifest) ⇒
                val schemaV1JsonBlob = SchemaV1.prettyPrint(schemaV1Manifest)
                PImageManifest.upsertSchemaV2(image, manifest, reference, configBlob,
                  schemaV1JsonBlob, rawManifest, sha256Digest)
                  .fast
                  .map {
                    case Validated.Valid(_) ⇒ Xor.right(sha256Digest)
                    case Validated.Invalid(e) ⇒
                      Xor.left(Unknown(e.map(_.message).mkString(";")))
                  }
              case Xor.Left(e) ⇒
                println(e)
                ???
            }
        }
    }
  }

  private def getImageConfig(configFile: java.io.File)(implicit mat: Materializer) =
    FileIO.fromPath(configFile.toPath).map(_.utf8String).runWith(Sink.head)
}
