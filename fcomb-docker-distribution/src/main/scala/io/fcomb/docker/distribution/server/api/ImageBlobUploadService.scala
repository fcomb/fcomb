package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import cats.data.Validated
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.docker.distribution.server.utils.BlobFile
import io.fcomb.models.docker.distribution.{ImageBlobState, Reference}
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}
import io.fcomb.models.{User ⇒ MUser}
import io.fcomb.persist.docker.distribution.{ImageBlob ⇒ PImageBlob}
import java.util.UUID
import scala.collection.immutable
import scala.compat.java8.OptionConverters._
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server.Directives._
import io.fcomb.models.errors._
import io.fcomb.json.docker.distribution.Formats._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._

import AuthenticationDirectives._
import ImageDirectives._

trait CommonDirectives {
  @inline
  def completeWithStatus(status: StatusCode): Route =
    complete(HttpResponse(status))

  @inline
  def completeNotFound(): Route =
    completeWithStatus(StatusCodes.NotFound)
}

object CommonDirectives extends CommonDirectives

import CommonDirectives._

object ImageBlobUploadService {
  def createBlob(imageName: String)(implicit req: HttpRequest): Route =
    authenticationUserBasic { user ⇒
      parameters('mount.?, 'from.?, 'digest.?) {
        (mountOpt, fromOpt, digestOpt) ⇒
          extractExecutionContext { implicit ec ⇒
            (mountOpt, fromOpt, digestOpt) match {
              case (Some(mount), Some(from), _) ⇒
                mountImageBlob(user, imageName, mount, from)
              case (_, _, Some(digest)) ⇒
                createImageBlob(user, imageName, digest)
              case _ ⇒
                createImageBlobUpload(user, imageName)
            }
          }
      }
    }

  private def createImageBlobUpload(user: MUser, imageName: String)(
    implicit
    ec:  ExecutionContext,
    req: HttpRequest
  ): Route = {
    val contentType = req.entity.contentType.mediaType.value
    onSuccess(PImageBlob.createByImageName(imageName, user.getId, contentType)) {
      case Validated.Valid(blob) ⇒
        val uuid = blob.getId
        val headers = immutable.Seq(
          Location(s"/v2/$imageName/blobs/uploads/$uuid"),
          `Docker-Upload-Uuid`(uuid),
          rangeHeader(0L, 0L)
        )
        respondWithHeaders(headers) {
          completeWithStatus(StatusCodes.Accepted)
        }
      case Validated.Invalid(e) ⇒
        complete(StatusCodes.Accepted, FailureResponse.fromExceptions(e))
    }
  }

  private def mountImageBlob(user: MUser, imageName: String, digest: String, from: String)(
    implicit
    ec:  ExecutionContext,
    req: HttpRequest
  ): Route = {
    imageByNameWithAcl(from, user) { fromImage ⇒
      onSuccess(PImageBlob.mount(fromImage.getId, imageName, Reference.getDigest(digest), user.getId)) {
        case Some(blob) ⇒
          val sha256Digest = blob.sha256Digest.get
          val headers = immutable.Seq(
            Location(s"/v2/$imageName/blobs/sha256:$sha256Digest"),
            `Docker-Content-Digest`("sha256", sha256Digest)
          )
          respondWithHeaders(headers) {
            completeWithStatus(StatusCodes.Created)
          }
        case None ⇒ createImageBlobUpload(user, imageName)
      }
    }
  }

  private def createImageBlob(user: MUser, imageName: String, digest: String)(
    implicit
    req: HttpRequest
  ): Route =
    extractMaterializer { implicit mat ⇒
      import mat.executionContext
      val contentType = req.entity.contentType.mediaType.value
      onSuccess(for {
        Validated.Valid(blob) ← PImageBlob.createByImageName(imageName, user.getId, contentType)
        (length, sha256Digest) ← BlobFile.uploadBlob(blob.getId, req.entity.dataBytes)
        _ ← PImageBlob.completeUploadOrDelete(blob.getId, blob.imageId, length, sha256Digest)
      } yield (blob, sha256Digest)) {
        case (blob, sha256Digest) ⇒
          val uuid = blob.getId
          if (Reference.getDigest(digest) == sha256Digest) {
            onSuccess(BlobFile.renameOrDelete(uuid, sha256Digest)) {
              val headers = immutable.Seq(
                Location(s"/v2/$imageName/blobs/sha256:$sha256Digest"),
                `Docker-Upload-Uuid`(uuid),
                `Docker-Content-Digest`("sha256", sha256Digest)
              )
              respondWithHeaders(headers) {
                completeWithStatus(StatusCodes.Created)
              }
            }
          }
          else {
            val res =
              for {
                _ ← BlobFile.destroyBlob(uuid)
                _ ← PImageBlob.destroy(uuid)
              } yield ()
            onSuccess(res) {
              complete(
                StatusCodes.BadRequest,
                DistributionErrorResponse.from(DistributionError.DigestInvalid())
              )
            }
          }
      }
    }

  def uploadBlobChunk(imageName: String, uuid: UUID)(
    implicit
    req: HttpRequest
  ) =
    authenticationUserBasic { user ⇒
      extractMaterializer { implicit mat ⇒
        optionalHeaderValueByType[`Content-Range`]() { rangeOpt ⇒
          imageByNameWithAcl(imageName, user) { image ⇒
            import mat.executionContext
            onSuccess(PImageBlob.findByImageIdAndUuid(image.getId, uuid)) {
              case Some(blob) if !blob.isUploaded ⇒
                val (rangeFrom, rangeTo) = rangeOpt match {
                  case Some(r) ⇒
                    val cr = r.contentRange
                    (cr.getSatisfiableFirst.asScala, cr.getSatisfiableLast.asScala)
                  case None ⇒ (None, None)
                }
                val isRangeValid = (rangeFrom, rangeTo) match {
                  case (Some(from), Some(to)) ⇒ to >= from
                  case _                      ⇒ true
                }
                if (!isRangeValid) {
                  complete(
                    StatusCodes.BadRequest,
                    DistributionErrorResponse.from(DistributionError.Unknown("Range is invalid"))
                  )
                }
                else if (rangeFrom.exists(_ != blob.length)) {
                  complete(
                    StatusCodes.BadRequest,
                    DistributionErrorResponse.from(DistributionError.Unknown("Range start not satisfy a blob file length"))
                  )
                }
                else {
                  val data = (rangeFrom, rangeTo) match {
                    case (Some(from), Some(to)) ⇒
                      req.entity.dataBytes.take(to - from + 1)
                    case _ ⇒ req.entity.dataBytes
                  }
                  val totalLengthFut =
                    for {
                      (length, digest) ← BlobFile.uploadBlobChunk(uuid, data)
                      totalLength = blob.length + length
                      _ ← PImageBlob.updateState(uuid, totalLength, digest, ImageBlobState.Uploading)
                    } yield totalLength
                  onSuccess(totalLengthFut) { totalLength ⇒
                    val headers = immutable.Seq(
                      Location(s"/v2/$imageName/blobs/$uuid"),
                      `Docker-Upload-Uuid`(uuid),
                      rangeHeader(0L, totalLength)
                    )
                    respondWithHeaders(headers) {
                      complete(StatusCodes.Accepted, HttpEntity.Empty)
                    }
                  }
                }
              case _ ⇒
                complete(
                  StatusCodes.NotFound,
                  DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
                )
            }
          }
        }
      }
    }

  def uploadComplete(imageName: String, uuid: UUID)(
    implicit
    req: HttpRequest
  ) =
    authenticationUserBasic { user ⇒
      parameters('digest) { digest ⇒
        extractMaterializer { implicit mat ⇒
          imageByNameWithAcl(imageName, user) { image ⇒
            import mat.executionContext
            onSuccess(PImageBlob.findByImageIdAndUuid(image.getId, uuid)) {
              case Some(blob) if !blob.isUploaded ⇒
                val uploadResFut =
                  if (blob.isCreated) BlobFile.uploadBlob(uuid, req.entity.dataBytes)
                  else BlobFile.uploadBlobChunk(uuid, req.entity.dataBytes)
                onSuccess(uploadResFut) {
                  case (length, sha256Digest) ⇒
                    complete {
                      if (sha256Digest == Reference.getDigest(digest)) {
                        val headers = immutable.Seq(
                          Location(s"/v2/$imageName/blobs/sha256:$sha256Digest"),
                          `Docker-Upload-Uuid`(uuid),
                          `Docker-Content-Digest`("sha256", sha256Digest)
                        )
                        for {
                          _ ← BlobFile.renameOrDelete(uuid, sha256Digest)
                          _ ← PImageBlob.completeUploadOrDelete(uuid, blob.imageId, length, sha256Digest)
                        } yield HttpResponse(StatusCodes.Created, headers)
                      }
                      else {
                        val e = DistributionErrorResponse.from(DistributionError.DigestInvalid())
                        Marshal(StatusCodes.BadRequest → e).to[HttpResponse]
                      }
                    }
                }
              case _ ⇒
                complete(
                  StatusCodes.NotFound,
                  DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
                )
            }
          }
        }
      }
    }

  def destroyBlobUpload(imageName: String, uuid: UUID) =
    authenticationUserBasic { user ⇒
      extractMaterializer { implicit mat ⇒
        imageByNameWithAcl(imageName, user) { image ⇒
          import mat.executionContext
          onSuccess(PImageBlob.findByImageIdAndUuid(image.getId, uuid)) {
            case Some(blob) if !blob.isUploaded ⇒
              complete(for {
                _ ← BlobFile.destroyBlob(blob.getId)
                _ ← PImageBlob.destroy(uuid)
              } yield HttpResponse(StatusCodes.NoContent))
            case _ ⇒
              complete(
                StatusCodes.NotFound,
                DistributionErrorResponse.from(DistributionError.BlobUploadInvalid())
              )
          }
        }
      }
    }

  private def rangeHeader(from: Long, length: Long) = {
    val to = if (from < length) length - 1 else from
    RangeCustom(from, to)
  }
}
