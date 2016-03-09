package io.fcomb.docker.distribution.server.api.services

import io.fcomb.api.services.{Service, ServiceContext, ServiceResult}
import io.fcomb.docker.distribution.server.api.services.headers._
import io.fcomb.persist.docker.distribution.{Blob ⇒ PBlob}
import io.fcomb.models.docker.distribution.{Blob ⇒ MBlob, _}
import io.fcomb.utils.StringUtils
import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.security.MessageDigest
import java.time.ZonedDateTime
import java.util.UUID

object ImageService extends Service {
  def create(name: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(PBlob.createByImageName(name, 1).map { res ⇒
      val headers = res.map { blob ⇒
        val uuid = blob.getId
        List(
          Location(s"/v2/$name/blobs/uploads/$uuid"),
          `Docker-Upload-Uuid`(uuid),
          range(0L, 0L)
        )
      }.getOrElse(List.empty)
      completeValidationWithoutResult(res, StatusCodes.Accepted, headers)
    })
  }

  def upload(name: String, uuid: UUID)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(PBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) ⇒
        val md = MessageDigest.getInstance("SHA-256")
        val file = imageFile(blob.getId.toString)
        for {
          _ ← ctx.requestContext.request.entity.dataBytes
            .map { chunk ⇒
              md.update(chunk.toArray)
              chunk
            }
            .runWith(akka.stream.scaladsl.FileIO.toFile(file))
          digest = StringUtils.hexify(md.digest)
          _ ← PBlob.uploadChunk(uuid, file.length, digest)
        } yield {
          println(s"digest: $digest")
          println(s"upload `$uuid` (${file.length} bytes) layout for image $name: $file")
          completeWithoutResult(StatusCodes.Accepted, List(
            Location(s"/v2/$name/blobs/$uuid"),
            `Docker-Upload-Uuid`(uuid),
            range(blob.length, file.length)
          ))
        }
      case None ⇒ Future.successful(completeNotFound())
    })
  }

  def uploadComplete(name: String, uuid: UUID)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(PBlob.findByImageAndUuid(name, uuid).flatMap {
      case Some((blob, _)) ⇒
        // TODO: append data from dataBytes if non empty
        val digest = getDigest(ctx.requestContext.request.uri.query().get("digest").get)
        if (blob.sha256Digest.contains(digest)) {
          PBlob.update(uuid)(_.copy(
            state = BlobState.Uploaded,
            uploadedAt = Some(ZonedDateTime.now())
          )).map { _ ⇒
            completeWithoutResult(StatusCodes.Created, List(
              Location(s"/v2/$name/blobs/sha256:$digest"),
              `Docker-Content-Digest`("sha256", digest)
            ))
          }
        }
        else ??? // TODO
      case None ⇒ Future.successful(completeNotFound())
    })
  }

  def show(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(PBlob.findByImageAndDigest(name, getDigest(digest)).flatMap {
      case Some((blob, _)) ⇒
        println(s"blob: $blob")
        ???
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
    println(s"reference: $reference")
    ???
  }

  private def getDigest(digest: String) =
    digest.split(':').last

  private def range(from: Long, length: Long) = {
    val to =
      if (from < length) length - 1
      else from
    RangeCustom(from, to)
  }

  private def imageFile(imageName: String) =
    new java.io.File(s"/tmp/blobs/${imageName.replaceAll("/", "_")}")
}
