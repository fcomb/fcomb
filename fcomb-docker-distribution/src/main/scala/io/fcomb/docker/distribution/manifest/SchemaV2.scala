package io.fcomb.docker.distribution.manifest

import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import cats.data.{Validated, Xor}
import cats.syntax.cartesian._
import io.circe._, io.circe.parser._, io.circe.syntax._
import io.fcomb.crypto.Jws
import io.fcomb.json.docker.distribution.Formats._
import io.fcomb.json.docker.distribution.Formats.decodeSchemaV1Protected
import io.fcomb.models.docker.distribution.SchemaV1.{Manifest ⇒ ManifestV1, Protected, Layer, FsLayer, LayerContainerConfig, Config}
import io.fcomb.models.docker.distribution.SchemaV2.{ImageConfig, Manifest ⇒ ManifestV2}
import io.fcomb.models.docker.distribution.{ImageManifest ⇒ MImageManifest, Image ⇒ MImage}, MImageManifest.sha256Prefix
import io.fcomb.models.errors.docker.distribution.DistributionError, DistributionError._
import io.fcomb.persist.docker.distribution.{ImageManifest ⇒ PImageManifest, ImageBlob ⇒ PImageBlob}
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.utils.{StringUtils, Units}, Units._
import org.apache.commons.codec.digest.DigestUtils
import org.jose4j.base64url.Base64Url
import scala.concurrent.{ExecutionContext, Future}

object SchemaV2 {
  def upsertAsImageManifest(
    image:       MImage,
    reference:   String,
    manifest:    ManifestV2,
    rawManifest: String
  )(implicit ec: ExecutionContext, mat: Materializer): Future[Xor[DistributionError, String]] = {
    val sha256Digest = DigestUtils.sha256Hex(rawManifest)
    PImageManifest.findByImageIdAndDigest(image.getId, sha256Digest).flatMap {
      case _ ⇒ ???
      case None ⇒
        val digests = (manifest.config :: manifest.layers)
          .map(_.parseDigest)
          .filterNot(_ == MImageManifest.emptyTarSha256Digest)
          .distinct
        val configDigest = digests.head
        (for {
          Some(configBlob) ← PImageBlob.findByImageIdAndDigest(image.getId, configDigest)
          _ = assert(configBlob.length <= 1.MB, "Config JSON size is more than 1 MB")
          configFile = BlobFile.blobFile(configDigest)
          imageConfig ← getImageConfig(configFile)
        } yield (configBlob, imageConfig)).flatMap {
          case (configBlob, imageConfig) ⇒
            SchemaV1.convertFromSchemaV2(image, manifest, imageConfig) match {
              case Xor.Right(schemaV1Manifest) ⇒
                val schemaV1Blob = schemaV1Manifest.asJson
                PImageManifest.upsertSchemaV2(image, manifest, configBlob, schemaV1Blob, sha256Digest)
                  .fast
                  .map {
                    case Validated.Valid(_) ⇒ Xor.right(sha256Digest)
                    case Validated.Invalid(e) ⇒
                      Xor.left(DistributionError.Unknown(e.map(_.message).mkString(";")))
                  }
              case _ ⇒ ???
            }
        }
    }
  }

  private def getImageConfig(configFile: java.io.File)(implicit mat: Materializer) = {
    FileIO.fromPath(configFile.toPath).map(_.utf8String).runWith(Sink.head)
  }
}
