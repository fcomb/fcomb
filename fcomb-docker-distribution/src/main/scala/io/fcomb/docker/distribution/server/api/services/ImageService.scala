package io.fcomb.docker.distribution.server.api.services

import io.fcomb.api.services.Service
import io.fcomb.docker.distribution.server.api.services.headers._
import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import org.apache.commons.codec.digest.DigestUtils
import java.util.UUID

object ImageService extends Service {
  def create(name: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    val uuid = java.util.UUID.randomUUID()
    completeWithoutResult(StatusCodes.Accepted, List(
      Location(s"/v2/$name/blobs/uploads/$uuid"),
      `Docker-Upload-Uuid`(uuid),
      RangeCustom(0L, 0L)
    ))
  }

  def upload(name: String, uuid: UUID)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    complete(ctx.requestContext.request.entity.toStrict(1.second).flatMap { entity ⇒
      // val digest = DigestUtils.sha256Hex(entity.getData.toArray)
      val blob = imageFile(name)
      entity.dataBytes.runWith(akka.stream.scaladsl.FileIO.toFile(blob)).map { _ ⇒
        println(s"upload `$uuid` (${blob.length} bytes) layout for image $name: $blob")
        completeWithoutResult(StatusCodes.Accepted, List(
          Location(s"/v2/$name/blobs/$uuid"),
          `Docker-Upload-Uuid`(uuid),
          // `Docker-Content-Digest`("sha256", digest),
          RangeCustom(0L, blob.length - 1)
        ))
      }
    })
  }

  def show(name: String, digest: String)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    ???
  }

  private def imageFile(imageName: String) =
    new java.io.File(s"/tmp/blobs/${imageName.replaceAll("/", "_")}")
}
