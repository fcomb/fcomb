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
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.data.Validated
import io.fcomb.server.CirceSupport._
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.json.models.errors.Formats._
import io.fcomb.json.models.errors.docker.distribution.Formats._
import io.fcomb.models.User
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution.{ImageBlobState, Reference}
import io.fcomb.models.errors._
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}
import io.fcomb.persist.docker.distribution.ImageBlobsRepo
import io.fcomb.docker.distribution.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.docker.distribution.server.ImageDirectives._
import java.util.UUID
import scala.collection.immutable
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext

object ImageBlobUploadsHandler {
  def createBlob(imageName: String)(implicit req: HttpRequest): Route =
    authenticateUserBasic { user =>
      parameters('mount.?, 'from.?, 'digest.?) { (mountOpt, fromOpt, digestOpt) =>
        extractExecutionContext { implicit ec =>
          (mountOpt, fromOpt, digestOpt) match {
            case (Some(mount), Some(from), _) =>
              mountImageBlob(user, imageName, mount, from)
            case (_, _, Some(digest)) =>
              createImageBlob(user, imageName, digest)
            case _ =>
              createImageBlobUpload(user, imageName)
          }
        }
      }
    }

  private def createImageBlobUpload(user: User, imageName: String)(
      implicit ec: ExecutionContext,
      req: HttpRequest
  ): Route = {
    val contentType = req.entity.contentType.mediaType.value
    imageByNameWithAcl(imageName, user.getId(), Action.Write) { image =>
      onSuccess(ImageBlobsRepo.create(image.getId(), contentType)) {
        case Validated.Valid(blob) =>
          val uuid = blob.getId
          val headers = immutable.Seq(
            Location(s"/v2/$imageName/blobs/uploads/$uuid"),
            `Docker-Upload-Uuid`(uuid),
            rangeHeader(0L, 0L)
          )
          respondWithHeaders(headers) {
            completeWithStatus(StatusCodes.Accepted)
          }
        case Validated.Invalid(e) =>
          complete((StatusCodes.Accepted, FailureResponse.fromExceptions(e)))
      }
    }
  }

  private def mountImageBlob(user: User, imageName: String, sha256Digest: String, from: String)(
      implicit ec: ExecutionContext,
      req: HttpRequest
  ): Route = {
    val userId = user.getId()
    imageByNameWithAcl(imageName, userId, Action.Write) { toImage =>
      imageByNameWithAcl(from, userId, Action.Read) { fromImage =>
        val digest      = Reference.getDigest(sha256Digest)
        val mountResFut = ImageBlobsRepo.mount(fromImage.getId(), toImage.getId(), digest, userId)
        onSuccess(mountResFut) {
          case Some(blob) =>
            val headers = immutable.Seq(
              Location(s"/v2/$imageName/blobs/$sha256Digest"),
              `Docker-Content-Digest`("sha256", digest)
            )
            respondWithHeaders(headers) {
              completeWithStatus(StatusCodes.Created)
            }
          case None => createImageBlobUpload(user, imageName)
        }
      }
    }
  }

  private def createImageBlob(user: User, imageName: String, sha256Digest: String)(
      implicit req: HttpRequest): Route =
    extractMaterializer { implicit mat =>
      imageByNameWithAcl(imageName, user.getId(), Action.Write) { image =>
        import mat.executionContext
        val contentType = req.entity.contentType.mediaType.value
        val blobResFut = for {
          Validated.Valid(blob) <- ImageBlobsRepo.create(image.getId(), contentType)
          (length, digest)      <- BlobFileUtils.uploadBlobChunk(blob.getId(), req.entity.dataBytes)
          _                     <- ImageBlobsRepo.completeUploadOrDelete(blob.getId(), blob.imageId, length, digest)
        } yield (blob, digest)
        onSuccess(blobResFut) {
          case (blob, digest) =>
            val uuid = blob.getId
            if (Reference.getDigest(sha256Digest) == digest) {
              onSuccess(BlobFileUtils.rename(uuid, digest)) {
                val headers = immutable.Seq(
                  Location(s"/v2/$imageName/blobs/$sha256Digest"),
                  `Docker-Upload-Uuid`(uuid),
                  `Docker-Content-Digest`("sha256", digest)
                )
                respondWithHeaders(headers) {
                  completeWithStatus(StatusCodes.Created)
                }
              }
            } else {
              onSuccess(ImageBlobsRepo.destroy(uuid)) { _ =>
                complete(
                  (
                    StatusCodes.BadRequest,
                    DistributionErrorResponse.from(DistributionError.DigestInvalid())
                  ))
              }
            }
        }
      }
    }

  def uploadBlobChunk(imageName: String, uuid: UUID)(
      implicit req: HttpRequest
  ) =
    authenticateUserBasic { user =>
      extractMaterializer { implicit mat =>
        optionalHeaderValueByType[`Content-Range`]() { rangeOpt =>
          imageByNameWithAcl(imageName, user.getId(), Action.Write) { image =>
            import mat.executionContext
            onSuccess(ImageBlobsRepo.findByImageIdAndUuid(image.getId(), uuid)) {
              case Some(blob) if !blob.isUploaded =>
                val (rangeFrom, rangeTo) = rangeOpt match {
                  case Some(r) =>
                    val cr = r.contentRange
                    (cr.getSatisfiableFirst.asScala, cr.getSatisfiableLast.asScala)
                  case None => (None, None)
                }
                val isRangeValid = (rangeFrom, rangeTo) match {
                  case (Some(from), Some(to)) => to >= from
                  case _                      => true
                }
                if (!isRangeValid) {
                  complete(
                    (
                      StatusCodes.BadRequest,
                      DistributionErrorResponse.from(DistributionError.Unknown("Range is invalid"))
                    ))
                } else if (rangeFrom.exists(_ != blob.length)) {
                  complete(
                    (
                      StatusCodes.BadRequest,
                      DistributionErrorResponse.from(
                        DistributionError.Unknown("Range start not satisfy a blob file length"))
                    ))
                } else {
                  val data = (rangeFrom, rangeTo) match {
                    case (Some(from), Some(to)) =>
                      req.entity.dataBytes.take(to - from + 1)
                    case _ => req.entity.dataBytes
                  }
                  val totalLengthFut = for {
                    (length, digest) <- BlobFileUtils.uploadBlobChunk(uuid, data)
                    totalLength = blob.length + length
                    _ <- ImageBlobsRepo.updateState(uuid,
                                                    totalLength,
                                                    digest,
                                                    ImageBlobState.Uploading)
                  } yield totalLength
                  onSuccess(totalLengthFut) { totalLength =>
                    val headers = immutable.Seq(
                      Location(s"/v2/$imageName/blobs/$uuid"),
                      `Docker-Upload-Uuid`(uuid),
                      rangeHeader(0L, totalLength)
                    )
                    respondWithHeaders(headers) {
                      complete((StatusCodes.Accepted, HttpEntity.Empty))
                    }
                  }
                }
              case _ =>
                complete(
                  (
                    StatusCodes.NotFound,
                    DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
                  ))
            }
          }
        }
      }
    }

  def uploadComplete(imageName: String, uuid: UUID)(implicit req: HttpRequest) =
    authenticateUserBasic { user =>
      parameters('digest) { sha256Digest =>
        extractMaterializer { implicit mat =>
          imageByNameWithAcl(imageName, user.getId(), Action.Write) { image =>
            import mat.executionContext
            onSuccess(ImageBlobsRepo.findByImageIdAndUuid(image.getId(), uuid)) {
              case Some(blob) if !blob.isUploaded =>
                onSuccess(BlobFileUtils.uploadBlobChunk(uuid, req.entity.dataBytes)) {
                  case (length, digest) =>
                    val totalLength = blob.length + length
                    complete {
                      if (digest == Reference.getDigest(sha256Digest)) {
                        val headers = immutable.Seq(
                          Location(s"/v2/$imageName/blobs/$sha256Digest"),
                          `Docker-Upload-Uuid`(uuid),
                          `Docker-Content-Digest`("sha256", digest)
                        )
                        for {
                          _ <- BlobFileUtils.rename(uuid, digest)
                          _ <- ImageBlobsRepo.completeUploadOrDelete(uuid,
                                                                     blob.imageId,
                                                                     totalLength,
                                                                     digest)
                        } yield HttpResponse(StatusCodes.Created, headers)
                      } else {
                        val e = DistributionErrorResponse.from(DistributionError.DigestInvalid())
                        Marshal(StatusCodes.BadRequest -> e).to[HttpResponse]
                      }
                    }
                }
              case _ =>
                complete(
                  (
                    StatusCodes.NotFound,
                    DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
                  ))
            }
          }
        }
      }
    }

  def destroyBlobUpload(imageName: String, uuid: UUID) =
    authenticateUserBasic { user =>
      extractMaterializer { implicit mat =>
        imageByNameWithAcl(imageName, user.getId(), Action.Manage) { image =>
          import mat.executionContext
          onSuccess(ImageBlobsRepo.findByImageIdAndUuid(image.getId(), uuid)) {
            case Some(blob) if !blob.isUploaded =>
              complete(for {
                _ <- BlobFileUtils.destroyUploadBlob(blob.getId())
                _ <- ImageBlobsRepo.destroy(uuid)
              } yield HttpResponse(StatusCodes.NoContent))
            case _ =>
              complete(
                (
                  StatusCodes.NotFound,
                  DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
                ))
          }
        }
      }
    }

  private def rangeHeader(from: Long, length: Long) = {
    val to = if (from < length) length - 1 else from
    RangeCustom(from, to)
  }
}
