/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.scaladsl._
import akka.util.ByteString
import io.fcomb.docker.distribution.server.AuthenticationDirectives._
import io.fcomb.docker.distribution.server.CommonDirectives._
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.docker.distribution.server.ImageDirectives._
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution._
import io.fcomb.models.errors.docker.distribution.DistributionError
import io.fcomb.persist.docker.distribution.ImageBlobsRepo
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.PersistDirectives._
import scala.collection.immutable
import scala.compat.java8.OptionConverters._
import scala.concurrent.duration._
import scala.concurrent.Future

object ImageBlobsHandler {
  def show(imageName: String, digest: String)(implicit config: ApiHandlerConfig) =
    tryAuthenticateUserBasic.apply { userOpt =>
      imageByNameWithReadAcl(imageName, userOpt.flatMap(_.id)).apply { image =>
        import config.ec
        transact(ImageBlobsRepo.findUploaded(image.getId(), digest)).apply {
          case Some(blob) =>
            val headers = immutable.Seq(
              `Docker-Content-Digest`("sha256", digest),
              etagHeader(digest),
              `Accept-Ranges`(RangeUnits.Bytes),
              cacheHeader
            )
            complete(
              HttpResponse(
                StatusCodes.OK,
                headers,
                HttpEntity(contentType(blob.contentType), blob.length, Source.empty)
              )
            )
          case _ => completeNotFound()
        }
      }
    }

  private val emptyTarSource = Source.single(ByteString(ImageManifest.emptyTar))

  def download(imageName: String, digest: String)(implicit config: ApiHandlerConfig) =
    tryAuthenticateUserBasic.apply { userOpt =>
      imageByNameWithReadAcl(imageName, userOpt.flatMap(_.id)).apply { image =>
        import config._
        transact(ImageBlobsRepo.findUploaded(image.getId(), digest)).apply {
          case Some(blob) =>
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
                    Source.single(
                      ByteString(
                        ImageManifest.emptyTar.drop(offset.toInt).take(chunkLength.toInt)))
                  else BlobFileUtils.streamBlob(digest, offset, chunkLength)
                complete(
                  HttpResponse(
                    StatusCodes.PartialContent,
                    headers,
                    HttpEntity(ct, chunkLength, source)
                  ))
              case _ =>
                optionalHeaderValueByType[`If-None-Match`]() {
                  case Some(`If-None-Match`(EntityTagRange.Default(EntityTag(digest, _) +: _))) =>
                    completeWithStatus(StatusCodes.NotModified)
                  case _ =>
                    val headers = immutable.Seq(
                      `Accept-Ranges`(RangeUnits.Bytes),
                      etagHeader(digest),
                      `Docker-Content-Digest`("sha256", digest),
                      cacheHeader
                    )
                    val source =
                      if (digest == ImageManifest.emptyTarSha256Digest) emptyTarSource
                      else FileIO.fromPath(BlobFileUtils.getFile(blob).toPath)
                    complete(
                      HttpResponse(
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

  def destroy(imageName: String, digest: String)(implicit config: ApiHandlerConfig) =
    authenticateUserBasic.apply { user =>
      imageByNameWithAcl(imageName, user.getId(), Action.Manage).apply { image =>
        import config._
        transact(ImageBlobsRepo.findUploaded(image.getId(), digest)).apply {
          case Some(blob) =>
            transact(ImageBlobsRepo.tryDestroy(blob.getId())).apply {
              case Right(_) =>
                val fut = db.run(ImageBlobsRepo.existByDigest(digest)).flatMap { exist =>
                  if (exist) Future.unit
                  else BlobFileUtils.destroyUploadBlob(blob.getId())
                }
                onSuccess(fut)(completeNoContent())
              case Left(msg) => completeError(DistributionError.unknown(msg))
            }
          case _ => completeError(DistributionError.blobUploadInvalid)
        }
      }
    }

  private val cacheHeader = `Cache-Control`(CacheDirectives.`max-age`(365.days.toSeconds))

  private def etagHeader(digest: String) = ETag(s"sha256:$digest")

  private def contentType(contentType: String) =
    ContentType.parse(contentType) match {
      case Right(res) => res
      case Left(_)    => `application/octet-stream`
    }
}
