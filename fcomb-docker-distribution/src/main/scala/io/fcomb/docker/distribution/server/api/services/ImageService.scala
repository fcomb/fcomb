package io.fcomb.docker.distribution.server.api.services

import io.fcomb.api.services.{Service, ServiceContext, ServiceResult}
import io.fcomb.docker.distribution.server.api.services.headers._
import io.fcomb.docker.distribution.server.services.ImageBlobPushProcessor
import io.fcomb.persist.docker.distribution.{Blob ⇒ PBlob}
import io.fcomb.persist.User
import io.fcomb.models.docker.distribution.{Blob ⇒ MBlob, _}
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}
import io.fcomb.utils.{Config, StringUtils}
import io.fcomb.json._
import akka.actor._
import akka.http.scaladsl.model._
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
      (for {
        user ← User.first
        res ← PBlob.createByImageName(name, user.getId)
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
    complete {
      (for {
        user ← User.first
        scalaz.Success(blob) ← PBlob.createByImageName(name, user.getId)
        file = imageFile(blob.getId.toString)
        sha256Digest ← writeFile(source, file)
      } yield (blob.getId, sha256Digest, file)).flatMap {
        case (uuid, sha256Digest, file) ⇒
          if (getDigest(digest) == sha256Digest) {
            PBlob.updateState(uuid, file.length, Some(sha256Digest), BlobState.Uploaded).map { _ ⇒
              completeWithoutResult(StatusCodes.Created, List(
                Location(s"/v2/$name/blobs/sha256:$sha256Digest"),
                `Docker-Upload-Uuid`(uuid)
              ))
            }
          }
          else {
            for {
              _ ← Future(blocking(file.delete()))
              _ ← PBlob.destroy(uuid)
            } yield completeErrors(Seq(DistributionError.DigestInvalid()))(StatusCodes.BadRequest)
          }
      }
    }
  }

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
    complete(PBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) ⇒
        assert(blob.state === BlobState.Created) // TODO: monolithic cannot be uploaded through chunk;move into FSM
        val md = MessageDigest.getInstance("SHA-256")
        val file = imageFile(blob.getId.toString)
        for {
          _ ← ctx.requestContext.request.entity.dataBytes.map { chunk ⇒
            md.update(chunk.toArray)
            chunk
          }.runWith(akka.stream.scaladsl.FileIO.toFile(file))
          // TODO: check file for 0 size
          digest = StringUtils.hexify(md.digest)
          // TODO: check digest for unique
          _ ← PBlob.updateState(uuid, file.length, Some(digest), BlobState.Uploaded)
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
    complete(PBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) ⇒
        assert(blob.state === BlobState.Created || blob.state === BlobState.Uploading) // TODO: move into FSM
        val contentRange = ctx.requestContext.request.header[`Content-Range`].get
        val rangeFrom = contentRange.contentRange.getSatisfiableFirst.asScala.get
        val rangeTo = contentRange.contentRange.getSatisfiableLast.asScala.get
        assert(rangeFrom == blob.length)
        assert(rangeTo >= rangeFrom)
        val file = imageFile(blob.getId.toString)
        for {
          (length, digest) ← ImageBlobPushProcessor.uploadChunk(
            blob.getId,
            ctx.requestContext.request.entity.dataBytes.take(rangeTo - rangeFrom + 1),
            file
          )
          // TODO: check file for 0 size
          _ ← PBlob.updateState(uuid, blob.length + length, Some(digest), BlobState.Uploading)
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
    complete(PBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) ⇒
        if (blob.state === BlobState.Uploaded)
          Future.successful(completeWithoutResult(StatusCodes.Created, headers))
        else {
          assert(blob.state === BlobState.Uploading) // TODO
          val file = imageFile(blob.getId.toString)
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
                PBlob.updateState(uuid, blob.length + length, Some(digest), BlobState.Uploaded).map { _ ⇒ // TODO: update uploadedAt
                  completeWithoutResult(StatusCodes.Created, headers)
                }
              }
              else {
                for {
                  _ ← Future(blocking(file.delete))
                  _ ← PBlob.destroy(uuid)
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
    complete(PBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) if blob.state === BlobState.Created || blob.state === BlobState.Uploading ⇒
        val file = imageFile(blob.getId.toString)
        for {
          _ ← Future(blocking(file.delete))
          _ ← PBlob.destroy(uuid)
        } yield completeWithoutContent()
      case _ ⇒ Future.successful(completeNotFound)
    })
  }

  def show(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val sha256Digest = getDigest(digest)
    complete(PBlob.findByImageAndDigest(name, sha256Digest).map {
      case Some((blob, _)) ⇒
        println(s"blob: $blob")
        complete(
          Source.empty,
          StatusCodes.OK,
          ContentTypes.`application/octet-stream`,
          List(
            `Docker-Content-Digest`("sha256", sha256Digest),
            ETag(digest),
            cacheHeader
          ),
          contentLength = Some(blob.length)
        )
      case None ⇒ completeNotFound()
    })
  }

  def download(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val sha256Digest = getDigest(digest)
    println(s"sha256Digest: $sha256Digest")
    complete(PBlob.findByImageAndDigest(name, sha256Digest).map {
      case Some((blob, _)) ⇒
        complete(
          FileIO.fromFile(imageFile(blob.getId.toString)),
          StatusCodes.OK,
          ContentTypes.`application/octet-stream`,
          List(`Docker-Content-Digest`("sha256", sha256Digest)),
          contentLength = Some(blob.length)
        )
      case None ⇒ completeNotFound()
    })
  }

  def manifestUpload(name: String, reference: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    import scala.concurrent.duration._
    complete(ctx.requestContext.request.entity.toStrict(1.second).map { entity ⇒
      println(s"entity: ${entity.getData.utf8String}")
      requestBodyAs[Manifest] { req ⇒
        println(s"reference: $reference, manifest: $req")
        val digest = getDigest(req match {
          case m: ManifestV1 ⇒ m.fsLayers.head.blobSum
          case m: ManifestV2 ⇒ m.layers.head.digest
        })
        println(s"digest: $digest")
        completeWithoutResult(StatusCodes.Created, List(
          `Docker-Content-Digest`("sha256", digest)
        ))
      }
    })
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

  private def imageFile(imageName: String) =
    new File(s"${Config.docker.distribution.imageStorage}/${imageName.replaceAll("/", "_")}")
}
