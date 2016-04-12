package io.fcomb.docker.distribution.server.api.services

import io.fcomb.api.services.{Service, ServiceContext, ServiceResult}
import io.fcomb.docker.distribution.server.api.services.headers._
import io.fcomb.docker.distribution.server.services.ImageBlobPushProcessor
import io.fcomb.docker.distribution.server.api.services.ContentTypes.`application/vnd.docker.distribution.manifest.v2+json`
import io.fcomb.persist.docker.distribution.{ImageBlob ⇒ PImageBlob, Image ⇒ PImage, ImageManifest ⇒ PImageManifest}
import io.fcomb.persist.User
import io.fcomb.models.docker.distribution.{ImageBlob ⇒ MImageBlob, _}
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}
import io.fcomb.utils.{Config, StringUtils}
import io.fcomb.json._
import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/octet-stream`
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import cats.data.Xor
import cats.syntax.eq._
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.concurrent.duration._
import scala.compat.java8.OptionConverters._
import spray.json.JsonWriter
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.nio.file.StandardOpenOption
import java.io.File
import java.util.UUID

// TODO: move blob upload methods into BlobUploadService
object ImageService extends Service {
  def createBlobUpload(name: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete {
      val mount = ctx.requestContext.request.uri.query().get("mount")
      val from = ctx.requestContext.request.uri.query().get("from")
      val contentType = ctx.requestContext.request.entity.contentType.mediaType.value
      (for {
        user ← User.first
        res ← PImageBlob.createByImageName(name, user.getId, contentType)
      } yield res).map { res ⇒
        val headers = res.map { blob ⇒
          val uuid = blob.getId
          List(
            Location(s"/v2/$name/blobs/uploads/$uuid"),
            `Docker-Upload-Uuid`(uuid),
            range(0L, 0L)
          )
        }.getOrElse(List.empty)
        completeValidationWithoutResult(res, StatusCodes.Accepted, headers)
      }
    }
  }

  def completeErrors(errors: Seq[DistributionError])(statusCode: StatusCode)(
    implicit
    ctx: ServiceContext
  ) =
    complete(DistributionErrorResponse(errors), statusCode)

  def createBlob(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val source = ctx.requestContext.request.entity.dataBytes
    val contentType = ctx.requestContext.request.entity.contentType.mediaType.value
    complete {
      (for {
        user ← User.first
        scalaz.Success(blob) ← PImageBlob.createByImageName(name, user.getId, contentType)
        file = blobFile(blob.getId.toString)
        sha256Digest ← writeFile(source, file)
        fileLength ← Future(blocking(file.length))
        _ ← PImageBlob.completeUpload(blob.getId, fileLength, sha256Digest)
      } yield (blob, sha256Digest, file)).flatMap {
        case (blob, sha256Digest, file) ⇒
          if (getDigest(digest) == sha256Digest) {
            renameFileToDigest(file, sha256Digest).map { _ ⇒
              completeWithoutResult(StatusCodes.Created, List(
                Location(s"/v2/$name/blobs/sha256:$sha256Digest"),
                `Docker-Upload-Uuid`(blob.getId),
                `Docker-Content-Digest`("sha256", sha256Digest)
              ))
            }
          }
          else {
            for {
              _ ← Future(blocking(file.delete()))
              _ ← PImageBlob.destroy(blob.getId)
            } yield completeErrors(Seq(DistributionError.DigestInvalid()))(StatusCodes.BadRequest)
          }
      }
    }
  }

  private def renameFileToDigest(file: File, digest: String)(
    implicit
    ec: ExecutionContext
  ) =
    Future(blocking {
      val newFile = blobFile(digest)
      if (!newFile.exists()) file.renameTo(newFile)
    })

  private def writeFile(
    source: Source[ByteString, Any],
    file:   File
  )(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = {
    val md = MessageDigest.getInstance("SHA-256")
    source.map { chunk ⇒
      md.update(chunk.toArray)
      chunk
    }.runWith(FileIO.toFile(file)).map { _ ⇒
      StringUtils.hexify(md.digest)
    }
  }

  def uploadBlob(name: String, uuid: UUID)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(PImageBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) ⇒
        assert(blob.state === ImageBlobState.Created) // TODO: monolithic cannot be uploaded through chunk;move into FSM
        val md = MessageDigest.getInstance("SHA-256")
        val file = blobFile(blob.getId.toString)
        for {
          _ ← ctx.requestContext.request.entity.dataBytes.map { chunk ⇒
            md.update(chunk.toArray)
            chunk
          }.runWith(akka.stream.scaladsl.FileIO.toFile(file))
          // TODO: check file for 0 size
          digest = StringUtils.hexify(md.digest)
          // TODO: check digest for unique
          _ ← PImageBlob.completeUpload(uuid, file.length, digest)
        } yield completeWithoutResult(StatusCodes.Created, List(
          Location(s"/v2/$name/blobs/sha256:$digest"),
          `Docker-Upload-Uuid`(uuid),
          `Docker-Content-Digest`("sha256", digest)
        ))
      case None ⇒ Future.successful(completeNotFound())
    })
  }

  def uploadBlobChunk(name: String, uuid: UUID)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(PImageBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) ⇒
        assert(blob.state === ImageBlobState.Created || blob.state === ImageBlobState.Uploading) // TODO: move into FSM
        // TODO: support content range and validate if it exists (Chunked and Streamed upload)
        // val contentRange = ctx.requestContext.request.header[`Content-Range`].get
        // val rangeFrom = contentRange.contentRange.getSatisfiableFirst.asScala.get
        // val rangeTo = contentRange.contentRange.getSatisfiableLast.asScala.get
        // assert(rangeFrom == blob.length)
        // assert(rangeTo >= rangeFrom)
        val file = blobFile(blob.getId.toString)
        for {
          (length, digest) ← ImageBlobPushProcessor.uploadChunk(
            blob.getId,
            ctx.requestContext.request.entity.dataBytes, // .take(rangeTo - rangeFrom + 1),
            file
          )
          // TODO: check file for 0 size
          _ ← PImageBlob.updateState(uuid, blob.length + length, digest, ImageBlobState.Uploading)
        } yield completeWithoutResult(StatusCodes.Accepted, List(
          Location(s"/v2/$name/blobs/$uuid"),
          `Docker-Upload-Uuid`(uuid),
          range(0L, file.length)
        ))
      case None ⇒ Future.successful(completeNotFound())
    })
  }

  def uploadComplete(name: String, uuid: UUID)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val queryDigest = getDigest(ctx.requestContext.request.uri.query().get("digest").get)
    val headers = List(
      Location(s"/v2/$name/blobs/sha256:$queryDigest"),
      `Docker-Content-Digest`("sha256", queryDigest)
    )
    complete(PImageBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) ⇒
        if (blob.state === ImageBlobState.Uploaded)
          Future.successful(completeWithoutResult(StatusCodes.Created, headers))
        else {
          assert(blob.state === ImageBlobState.Uploading) // TODO
          val file = blobFile(blob.getId.toString)
          (for {
            (length, digest) ← ImageBlobPushProcessor.uploadChunk(
              uuid,
              ctx.requestContext.request.entity.dataBytes,
              file
            )
            Xor.Right(_) ← ImageBlobPushProcessor.stop(blob.getId)
          } yield (length, digest)).flatMap {
            case (length, digest) ⇒
              if (digest == queryDigest) {
                PImageBlob.completeUpload(uuid, blob.length + length, digest).map { _ ⇒
                  completeWithoutResult(StatusCodes.Created, headers)
                }
              }
              else {
                for {
                  _ ← Future(blocking(file.delete))
                  _ ← PImageBlob.destroy(uuid)
                } yield completeErrors(Seq(DistributionError.DigestInvalid()))(StatusCodes.BadRequest)
              }
          }
        }
      case None ⇒ Future.successful(completeNotFound())
    })
  }

  def destroyBlobUpload(name: String, uuid: UUID)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(PImageBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) if blob.state === ImageBlobState.Created || blob.state === ImageBlobState.Uploading ⇒
        val file = blobFile(blob.getId.toString)
        for {
          _ ← Future(blocking(file.delete))
          _ ← PImageBlob.destroy(uuid)
        } yield completeWithoutContent()
      case _ ⇒ Future.successful(completeNotFound)
    })
  }

  def destroyBlob(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val sha256Digest = getDigest(digest)
    complete(PImageBlob.findByImageAndDigest(name, sha256Digest).flatMap {
      case Some((blob, _)) if blob.state === ImageBlobState.Uploaded ⇒
        val file = blobFile(blob.getId.toString)
        for {
          _ ← Future(blocking(file.delete)) // TODO: only if file exists once
          _ ← PImageBlob.destroy(blob.getId) // TODO: destroy only unlinked blob
        } yield completeWithoutContent()
      case _ ⇒ Future.successful(completeNotFound)
    })
  }

  def showBlob(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val sha256Digest = getDigest(digest)
    complete(PImageBlob.findByImageAndDigest(name, sha256Digest).map {
      case Some((blob, _)) ⇒
        println(s"blob: $blob")
        complete(
          Source.empty,
          StatusCodes.OK,
          `application/octet-stream`,
          List(
            `Docker-Content-Digest`("sha256", sha256Digest),
            ETag(digest),
            `Accept-Ranges`(RangeUnits.Bytes), // TODO: spec
            cacheHeader
          ),
          contentLength = Some(blob.length)
        )
      case None ⇒ completeNotFound()
    })
  }

  def downloadBlob(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val sha256Digest = getDigest(digest)
    println(s"sha256Digest: $sha256Digest")
    complete(PImageBlob.findByImageAndDigest(name, sha256Digest).map {
      case Some((blob, _)) ⇒
        val source = FileIO.fromFile(blobFile(blob))
        ctx.requestContext.request.header[Range] match {
          case Some(range) ⇒
            val r = range.ranges.head // TODO
            val offset: Long = r.getOffset.asScala.orElse(r.getSliceFirst.asScala).getOrElse(0L)
            val limit: Long = r.getSliceLast.asScala.map(_ - offset).getOrElse(blob.length)
            complete(
              source.drop(offset).take(limit),
              StatusCodes.PartialContent,
              `application/octet-stream`,
              List(`Content-Range`(ContentRange(offset, limit, blob.length))),
              contentLength = Some(blob.length)
            )
          case None ⇒
            complete(
              source,
              StatusCodes.OK,
              `application/octet-stream`,
              List(`Docker-Content-Digest`("sha256", sha256Digest)),
              contentLength = Some(blob.length)
            )
        }
      case None ⇒ completeNotFound()
    })
  }

  def getManifest(name: String, reference: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(for {
      Some(imageId) ← PImage.findIdByName(name)
      manifest ← PImageManifest.findByImageIdAndReferenceAsManifestV2(imageId, reference)
    } yield completeOrNotFound(manifest, StatusCodes.OK))
  }

  def uploadManifest(name: String, reference: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    requestBodyAs[Manifest] { manifest ⇒
      complete(ctx.requestContext.request.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).flatMap { rawManifest ⇒
        assert(ctx.requestContext.request.entity.contentType == `application/vnd.docker.distribution.manifest.v2+json`) // TODO
        PImageManifest.upsertByRequest(name, reference, manifest, rawManifest.utf8String).map {
          case res @ scalaz.Success(m) ⇒
            completeWithoutResult(StatusCodes.Created, List(
              `Docker-Content-Digest`("sha256", m.sha256Digest)
            ))
          case e ⇒ completeValidationWithoutContent(e)
        }
      })
    }
  }

  def destroyManifest(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(for {
      Some(imageId) ← PImage.findIdByName(name)
      _ ← PImageManifest.destroy(imageId, getDigest(digest))
    } yield completeWithoutContent())
  }

  def catalog(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    // TODO: add apgination
    complete(PImage.repositories, StatusCodes.OK)
  }

  private val cacheHeader =
    `Cache-Control`(CacheDirectives.`max-age`(365.days.toSeconds))

  private def getDigest(digest: String) =
    digest.split(':').last

  private def range(from: Long, length: Long) = {
    val to =
      if (from < length) length - 1
      else from
    RangeCustom(from, to)
  }

  private def blobFile(imageName: String): File =
    new File(s"${Config.docker.distribution.imageStorage}/${imageName.replaceAll("/", "_")}")

  private def blobFile(blob: MImageBlob): File = {
    val filename =
      if (blob.isUploaded) blob.sha256Digest.get
      else blob.getId.toString
    blobFile(filename)
  }
}
