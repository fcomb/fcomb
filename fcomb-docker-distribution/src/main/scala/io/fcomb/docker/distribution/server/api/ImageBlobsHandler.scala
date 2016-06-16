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

package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.util.FastFuture, FastFuture._
import akka.stream.scaladsl._
import akka.util.ByteString
import cats.data.Xor
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.fcomb.docker.distribution.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.docker.distribution.server.ImageDirectives._
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.docker.distribution.utils.BlobFile
import io.fcomb.json.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}
import io.fcomb.persist.docker.distribution.ImageBlobsRepo
import scala.collection.immutable
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.util.{Right, Left}

object ImageBlobsHandler {
  def showBlob(imageName: String, digest: String) =
    authenticateUserBasic { user =>
      extractMaterializer { implicit mat =>
        imageByNameWithAcl(imageName, user, Action.Read) { image =>
          import mat.executionContext
          val sha256Digest = Reference.getDigest(digest)
          onSuccess(ImageBlobsRepo.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) if blob.isUploaded =>
              complete(
                HttpResponse(
                  StatusCodes.OK,
                  immutable.Seq(
                    `Docker-Content-Digest`("sha256", sha256Digest),
                    ETag(digest),
                    `Accept-Ranges`(RangeUnits.Bytes),
                    cacheHeader
                  ),
                  HttpEntity(contentType(blob.contentType), blob.length, Source.empty)
                )
              )
            case _ => completeNotFound()
          }
        }
      }
    }

  private val emptyTarSource = Source.single(ByteString(ImageManifest.emptyTar))

  def downloadBlob(imageName: String, digest: String)(
      implicit req: HttpRequest
  ) =
    authenticateUserBasic { user =>
      extractMaterializer { implicit mat =>
        imageByNameWithAcl(imageName, user, Action.Read) { image =>
          import mat.executionContext
          val sha256Digest = Reference.getDigest(digest)
          onSuccess(ImageBlobsRepo.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) if blob.isUploaded =>
              val ct = contentType(blob.contentType)
              optionalHeaderValueByType[Range]() {
                case Some(Range(_, range +: _)) =>
                  val offset: Long =
                    range.getOffset.asScala.orElse(range.getSliceFirst.asScala).getOrElse(0L)
                  val limit: Long = range.getSliceLast.asScala.getOrElse(blob.length)
                  val chunkLength = limit - offset
                  val headers = immutable.Seq(
                    `Content-Range`(ContentRange(offset, limit, blob.length)),
                    `Accept-Ranges`(RangeUnits.Bytes)
                  )
                  val source =
                    if (digest == ImageManifest.emptyTarSha256Digest)
                      Source.single(ByteString(
                          ImageManifest.emptyTar.drop(offset.toInt).take(chunkLength.toInt)))
                    else BlobFile.streamBlob(sha256Digest, offset, chunkLength)
                  complete(HttpResponse(
                      StatusCodes.PartialContent,
                      headers,
                      HttpEntity(ct, chunkLength, source)
                    ))
                case _ =>
                  optionalHeaderValueByType[`If-None-Match`]() {
                    case Some(
                        `If-None-Match`(EntityTagRange.Default(EntityTag(digest, _) +: _))) =>
                      completeWithStatus(StatusCodes.NotModified)
                    case _ =>
                      val headers = immutable.Seq(
                        `Accept-Ranges`(RangeUnits.Bytes),
                        ETag(digest),
                        `Docker-Content-Digest`("sha256", sha256Digest),
                        cacheHeader
                      )
                      val source =
                        if (digest == ImageManifest.emptyTarSha256Digest) emptyTarSource
                        else FileIO.fromPath(BlobFile.getFile(blob).toPath)
                      complete(HttpResponse(
                          StatusCodes.OK,
                          headers,
                          HttpEntity(ct, blob.length, source)
                        ))
                  }
              }
            case _ => completeNotFound()
          }
        }
      }
    }

  def destroyBlob(imageName: String, digest: String) =
    authenticateUserBasic { user =>
      extractMaterializer { implicit mat =>
        imageByNameWithAcl(imageName, user, Action.Manage) { image =>
          import mat.executionContext
          val sha256Digest = Reference.getDigest(digest)
          onSuccess(ImageBlobsRepo.findByImageIdAndDigest(image.getId, sha256Digest)) {
            case Some(blob) if blob.isUploaded =>
              val res = ImageBlobsRepo.tryDestroy(blob.getId).flatMap {
                case Xor.Right(_) =>
                  ImageBlobsRepo
                    .existByDigest(digest)
                    .flatMap {
                      case false => BlobFile.destroyBlob(blob.getId)
                      case true  => FastFuture.successful(())
                    }
                    .fast
                    .map(_ => HttpResponse(StatusCodes.NoContent))
                case Xor.Left(msg) =>
                  val e = DistributionErrorResponse.from(DistributionError.Unknown(msg))
                  Marshal(StatusCodes.InternalServerError -> e).to[HttpResponse]
              }
              complete(res)
            case _ =>
              complete((
                  StatusCodes.NotFound,
                  DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
                ))
          }
        }
      }
    }

  private val cacheHeader = `Cache-Control`(
    CacheDirectives.`max-age`(365.days.toSeconds)
  )

  private def contentType(contentType: String) =
    ContentType.parse(contentType) match {
      case Right(res) => res
      case Left(_)    => `application/octet-stream`
    }
}
