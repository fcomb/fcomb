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
import akka.stream.scaladsl.{FileIO, Sink}
import cats.data.{Validated, Xor}
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.models.docker.distribution.ImageManifest.sha256Prefix
import io.fcomb.models.docker.distribution.SchemaV2.{Manifest => ManifestV2}
import io.fcomb.models.docker.distribution.{Image, Reference}
import io.fcomb.models.errors.docker.distribution.DistributionError
import io.fcomb.persist.docker.distribution.{ImageBlobsRepo, ImageManifestsRepo}
import io.fcomb.services.EventService
import io.fcomb.utils.Units._
import org.apache.commons.codec.digest.DigestUtils
import scala.concurrent.{ExecutionContext, Future}

object SchemaV2 {
  def upsertAsImageManifest(
      image: Image,
      reference: Reference,
      manifest: ManifestV2,
      rawManifest: String,
      createdByUserId: Int
  )(implicit ec: ExecutionContext, mat: Materializer): Future[Xor[DistributionError, String]] = {
    val digest = DigestUtils.sha256Hex(rawManifest)
    ImageManifestsRepo.findByImageIdAndDigest(image.getId(), digest).flatMap {
      case Some(im) =>
        ImageManifestsRepo.updateTagsByReference(im, reference).fast.map(_ => Xor.Right(im.digest))
      case None =>
        val configDigest = manifest.config.getDigest
        ImageBlobsRepo.findByImageIdAndDigest(image.getId(), configDigest).flatMap {
          case Some(configBlob) =>
            if (configBlob.length > 1.MB)
              FastFuture.successful(unknowError("Config JSON size is more than 1 MB"))
            else {
              val configFile = BlobFileUtils.getBlobFilePath(configDigest)
              getImageConfig(configFile).flatMap { imageConfig =>
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

  private def getImageConfig(configFile: java.io.File)(implicit mat: Materializer) =
    FileIO.fromPath(configFile.toPath).map(_.utf8String).runWith(Sink.head)
}
