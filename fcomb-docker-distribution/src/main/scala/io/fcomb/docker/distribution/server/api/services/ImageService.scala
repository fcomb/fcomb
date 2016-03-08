package io.fcomb.docker.distribution.server.api.services

import io.fcomb.api.services.{Service, ServiceContext, ServiceResult}
import io.fcomb.docker.distribution.server.api.services.headers._
import io.fcomb.persist.docker.distribution.{Blob ⇒ PBlob}
import io.fcomb.models.docker.distribution.{Blob ⇒ MBlob}
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
import java.util.UUID

object ImageService extends Service {
  def create(name: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val uuid = java.util.UUID.randomUUID()
    complete(PBlob.createByImageName(name, 1).map { res ⇒
      val headers = List(
        Location(s"/v2/$name/blobs/uploads/$uuid"),
        `Docker-Upload-Uuid`(uuid),
        range(0L, 0L)
      )
      completeValidationWithoutResult(res, StatusCodes.Accepted, headers)
    })
  }

  def upload(name: String, uuid: UUID)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val blob = imageFile(name)
    val digest = MessageDigest.getInstance("SHA-256")
    complete(ctx.requestContext.request.entity.dataBytes
      .map { chunk ⇒
        digest.update(chunk.toArray)
        chunk
      }
      .runWith(akka.stream.scaladsl.FileIO.toFile(blob)).map { _ ⇒
        println(s"digest: ${StringUtils.hexify(digest.digest)}")
        println(s"upload `$uuid` (${blob.length} bytes) layout for image $name: $blob")
        completeWithoutResult(StatusCodes.Accepted, List(
          Location(s"/v2/$name/blobs/$uuid"),
          `Docker-Upload-Uuid`(uuid),
          // `Docker-Content-Digest`("sha256", digest),
          range(0L, blob.length)
        ))
      })
  }

  def uploadComplete(name: String, uuid: UUID)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val digest = getDigest(ctx.requestContext.request.uri.query().get("digest").get)
    completeWithoutResult(StatusCodes.Created, List(
      Location(s"/v2/$name/blobs/sha256:$digest"),
      `Docker-Content-Digest`("sha256", digest)
    ))
  }

  def show(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    ???
  }

  def download(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val file = imageFile(name)
    val blob = FileIO.fromFile(file)
    complete(
      blob,
      StatusCodes.OK,
      ContentTypes.`application/octet-stream`,
      List(`Docker-Content-Digest`("sha256", getDigest(digest))),
      contentLength = Some(file.length)
    )
  }

  private def findByDigest(image: String, digest: String)(
    f: MBlob ⇒ Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ) =
    complete(PBlob.findByImageAndDigest(image, digest).flatMap {
      case Some(blob) ⇒ f(blob._1)
      case None       ⇒ Future.successful(completeNotFound())
    })

  private def findByUuid(image: String, uuid: UUID)(
    f: MBlob ⇒ Future[ServiceResult]
  )(
    implicit
    ctx: ServiceContext,
    ec:  ExecutionContext
  ) =
    complete(PBlob.findByImageAndUuid(image, uuid).flatMap {
      case Some(blob) ⇒ f(blob._1)
      case None       ⇒ Future.successful(completeNotFound())
    })

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
