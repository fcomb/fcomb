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

package io.fcomb.docker.distribution.manifest

import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import cats.data.{Validated, Xor}
import io.circe.Json
import io.circe.jawn.CirceSupportParser._
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.models.docker.distribution.ImageManifest.sha256Prefix
import io.fcomb.models.docker.distribution.SchemaV2.{Manifest => ManifestV2}
import io.fcomb.models.docker.distribution.{Image, Reference}
import io.fcomb.models.errors.docker.distribution.DistributionError
import io.fcomb.persist.docker.distribution.{ImageBlobsRepo, ImageManifestsRepo}
import io.fcomb.services.EventService
import io.fcomb.utils.Units._
import java.io.File
import jawn.{AsyncParser, Parser}
import org.apache.commons.codec.digest.DigestUtils
import scala.concurrent.Future

object SchemaV2 {
  def upsertAsImageManifest(
      image: Image,
      reference: Reference,
      manifest: ManifestV2,
      rawManifest: String,
      createdByUserId: Int
  )(implicit mat: Materializer): Future[Xor[DistributionError, String]] = {
    import mat.executionContext
    val digest = DigestUtils.sha256Hex(rawManifest)
    ImageManifestsRepo.findByImageIdAndDigest(image.getId(), digest).flatMap {
      case Some(im) =>
        ImageManifestsRepo.updateTagsByReference(im, reference).fast.map(_ => Xor.Right(im.digest))
      case None =>
        val configDigest = manifest.config.getDigest
        ImageBlobsRepo.findByImageIdAndDigest(image.getId(), configDigest).flatMap {
          case Some(configBlob) =>
            if (configBlob.length > 16.MB)
              FastFuture.successful(unknowError("Config JSON size is more than 16 MB"))
            else {
              val configFile = BlobFileUtils.getBlobFilePath(configDigest)
              getImageConfig(configFile).flatMap {
                case Xor.Right(imageConfig) =>
                  SchemaV1.convertFromSchemaV2(image, manifest, imageConfig) match {
                    case Xor.Right(schemaV1JsonBlob) =>
                      ImageManifestsRepo
                        .upsertSchemaV2(image,
                                        manifest,
                                        reference,
                                        configBlob,
                                        schemaV1JsonBlob,
                                        rawManifest,
                                        digest)
                        .fast
                        .map {
                          case Validated.Valid(imageManifest) =>
                            EventService.pushRepoEvent(image,
                                                       imageManifest.getId(),
                                                       reference.value,
                                                       createdByUserId)
                            Xor.Right(digest)
                          case Validated.Invalid(e) => unknowError(e.map(_.message).mkString(";"))
                        }
                    case Xor.Left(e) => FastFuture.successful(unknowError(e))
                  }
                case Xor.Left(e) => FastFuture.successful(unknowError(e))
              }
            }
          case None =>
            FastFuture.successful(
              unknowError(s"Config blob `$sha256Prefix$configDigest` not found"))
        }
    }
  }

  private def unknowError(msg: String) =
    Xor.left[DistributionError, String](DistributionError.unknown(msg))

  private def getImageConfig(configFile: File)(
      implicit mat: Materializer): Future[Xor[String, Json]] = {
    import mat.executionContext
    val p = Parser.async[Json](mode = AsyncParser.SingleValue)
    FileIO
      .fromPath(configFile.toPath)
      .runFold(Xor.right[String, Option[Json]](None)) {
        case (Xor.Right(None), bs) =>
          p.absorb(bs.asByteBuffer) match {
            case Right(res) => Xor.Right(res.headOption)
            case Left(e)    => Xor.Left(e.msg)
          }
        case (res, _) => res
      }
      .fast
      .map(_.flatMap {
        case Some(res) => Xor.Right(res)
        case _ =>
          Xor.fromEither(p.finish).map(_.headOption.getOrElse(Json.Null)).leftMap(_.msg)
      })
  }
}
