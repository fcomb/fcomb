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

import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.data.Validated
import io.fcomb.docker.distribution.server.AuthenticationDirectives._
import io.fcomb.docker.distribution.server.CommonDirectives._
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.docker.distribution.server.ImageDirectives._
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution.{ImageBlobState, Reference}
import io.fcomb.models.errors.docker.distribution.DistributionError
import io.fcomb.models.User
import io.fcomb.persist.docker.distribution.ImageBlobsRepo
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.PersistDirectives._
import java.util.UUID
import scala.collection.immutable
import scala.compat.java8.OptionConverters._

object ImageBlobUploadsHandler {
  def create(imageName: String)(implicit config: ApiHandlerConfig): Route =
    extractRequest { req =>
      authenticateUserBasic.apply { user =>
        parameters(('mount.?, 'from.?, 'digest.?)) { (mountOpt, fromOpt, digestOpt) =>
          (mountOpt, fromOpt, digestOpt) match {
            case (Some(mount), Some(from), _) => mountImageBlob(req, user, imageName, mount, from)
            case (_, _, Some(digest))         => createImageBlob(req, user, imageName, digest)
            case _                            => createImageBlobUpload(req, user, imageName)
          }
        }
      }
    }

  private def createImageBlobUpload(req: HttpRequest, user: User, imageName: String)(
      implicit config: ApiHandlerConfig): Route = {
    val contentType = req.entity.contentType.mediaType.value
    imageByNameWithAcl(imageName, user.getId(), Action.Write).apply { image =>
      import config._
      transact(ImageBlobsRepo.create(image.getId(), contentType)).apply {
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
        case Validated.Invalid(errs) => completeErrors(errs)
      }
    }
  }

  private def mountImageBlob(req: HttpRequest,
                             user: User,
                             imageName: String,
                             sha256Digest: String,
                             from: String)(implicit config: ApiHandlerConfig): Route = {
    val userId = user.getId()
    imageByNameWithAcl(imageName, userId, Action.Write).apply { toImage =>
      imageByNameWithAcl(from, userId, Action.Read).apply { fromImage =>
        import config._
        val digest = Reference.getDigest(sha256Digest)
        transact(ImageBlobsRepo.mount(fromImage.getId(), toImage.getId(), digest, userId)).apply {
          case Some(blob) =>
            val headers = immutable.Seq(
              Location(s"/v2/$imageName/blobs/$sha256Digest"),
              `Docker-Content-Digest`("sha256", digest)
            )
            respondWithHeaders(headers) {
              completeWithStatus(StatusCodes.Created)
            }
          case None => createImageBlobUpload(req, user, imageName)
        }
      }
    }
  }

  private def createImageBlob(req: HttpRequest,
                              user: User,
                              imageName: String,
                              sha256Digest: String)(implicit config: ApiHandlerConfig): Route =
    imageByNameWithAcl(imageName, user.getId(), Action.Write).apply { image =>
      import config._
      val contentType = req.entity.contentType.mediaType.value
      val blobResFut = for {
        Validated.Valid(blob) <- db.run(ImageBlobsRepo.create(image.getId(), contentType))
        (length, digest)      <- BlobFileUtils.uploadBlobChunk(blob.getId(), req.entity.dataBytes)
        _ <- db.run(
          ImageBlobsRepo.completeUploadOrDelete(blob.getId(), blob.imageId, length, digest))
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
            transact(ImageBlobsRepo.destroy(uuid)).apply { _ =>
              completeError(DistributionError.digestInvalid)
            }
          }
      }
    }

  def uploadChunk(imageName: String, uuid: UUID)(implicit config: ApiHandlerConfig) =
    extractRequest { req =>
      authenticateUserBasic.apply { user =>
        optionalHeaderValueByType[`Content-Range`]() { rangeOpt =>
          imageByNameWithAcl(imageName, user.getId(), Action.Write).apply { image =>
            import config._
            transact(ImageBlobsRepo.findByImageIdAndUuid(image.getId(), uuid)).apply {
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
                if (!isRangeValid) completeError(DistributionError.unknown("Range is invalid"))
                else if (rangeFrom.exists(_ != blob.length))
                  completeError(
                    DistributionError.unknown("Range start not satisfy a blob file length"))
                else {
                  val data = (rangeFrom, rangeTo) match {
                    case (Some(from), Some(to)) =>
                      req.entity.dataBytes.take(to - from + 1)
                    case _ => req.entity.dataBytes
                  }
                  val totalLengthFut = for {
                    (length, digest) <- BlobFileUtils.uploadBlobChunk(uuid, data)
                    totalLength = blob.length + length
                    _ <- db.run(ImageBlobsRepo
                      .updateState(uuid, totalLength, digest, ImageBlobState.Uploading))
                  } yield totalLength
                  onSuccess(totalLengthFut) { totalLength =>
                    val headers = immutable.Seq(
                      Location(s"/v2/$imageName/blobs/$uuid"),
                      `Docker-Upload-Uuid`(uuid),
                      rangeHeader(0L, totalLength)
                    )
                    respondWithHeaders(headers) {
                      completeAccepted()
                    }
                  }
                }
              case _ => completeError(DistributionError.blobUploadInvalid)
            }
          }
        }
      }
    }

  def uploadComplete(imageName: String, uuid: UUID)(implicit config: ApiHandlerConfig) =
    extractRequest { req =>
      authenticateUserBasic.apply { user =>
        parameters('digest) { sha256Digest =>
          imageByNameWithAcl(imageName, user.getId(), Action.Write).apply { image =>
            import config._

            transact(ImageBlobsRepo.findByImageIdAndUuid(image.getId(), uuid)).apply {
              case Some(blob) if !blob.isUploaded =>
                onSuccess(BlobFileUtils.uploadBlobChunk(uuid, req.entity.dataBytes)) {
                  case (length, digest) =>
                    val totalLength = blob.length + length
                    if (digest == Reference.getDigest(sha256Digest)) {
                      val headers = immutable.Seq(
                        Location(s"/v2/$imageName/blobs/$sha256Digest"),
                        `Docker-Upload-Uuid`(uuid),
                        `Docker-Content-Digest`("sha256", digest)
                      )
                      val fut = for {
                        _ <- BlobFileUtils.rename(uuid, digest)
                        _ <- db.run(ImageBlobsRepo
                          .completeUploadOrDelete(uuid, blob.imageId, totalLength, digest))
                      } yield HttpResponse(StatusCodes.Created, headers)
                      onSuccess(fut)(complete(_))
                    } else completeError(DistributionError.digestInvalid)
                }
              case _ => completeError(DistributionError.blobUploadInvalid)
            }
          }
        }
      }
    }

  def destroy(imageName: String, uuid: UUID)(implicit config: ApiHandlerConfig) =
    authenticateUserBasic.apply { user =>
      imageByNameWithAcl(imageName, user.getId(), Action.Manage).apply { image =>
        import config._
        transact(ImageBlobsRepo.findByImageIdAndUuid(image.getId(), uuid)).apply {
          case Some(blob) if !blob.isUploaded =>
            complete(for {
              _ <- BlobFileUtils.destroyUploadBlob(blob.getId())
              _ <- db.run(ImageBlobsRepo.destroy(uuid))
            } yield HttpResponse(StatusCodes.NoContent))
          case _ => completeError(DistributionError.blobUploadInvalid)
        }
      }
    }

  private def rangeHeader(from: Long, length: Long) = {
    val to = if (from < length) length - 1 else from
    RangeCustom(from, to)
  }
}
