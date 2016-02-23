package io.fcomb.docker.distribution.api.services

import io.fcomb.api.services.ServiceRoute.Implicits._
import io.fcomb.docker.distribution.api.services.headers._
import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import scala.concurrent.duration._
import org.apache.commons.codec.digest.DigestUtils

object Routes {
  val apiVersion = "v2"

  private val versionCheckResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(ContentTypes.`application/json`, "{}")
  )

  private val versionHeader = `Docker-Distribution-Api-Version`("2.0")

  private val defaultHeaders = List(versionHeader)

  private val notFoundResponse = HttpResponse(StatusCodes.NotFound)

  def apply()(implicit sys: ActorSystem, mat: Materializer): Route = {
    import sys.dispatcher

    // format: OFF
    respondWithDefaultHeaders(defaultHeaders) {
      pathPrefix(apiVersion) {
        pathEndOrSingleSlash {
          get {
            { ctx: RequestContext =>
              ctx.complete(ctx.request.entity.toStrict(1.second).map { entity =>
                println(s"v2 check: uri: ${ctx.request.uri}, headers: ${ctx.request.headers}, entity: $entity")
                versionCheckResponse
              })
            }
          }
        } ~
        path(Segments ~ Slash.?) { segments =>
          { ctx: RequestContext =>
            val method = ctx.request.method
            println(s"image action => $method uri: ${ctx.request.uri}, segments: $segments, headers: ${ctx.request.headers}")
            segments.reverse match {
              case "uploads" :: "blobs" :: xs =>
                val name = imageName(xs)
                val uuid = java.util.UUID.randomUUID()
                ctx.complete(HttpResponse(StatusCodes.Accepted, headers = List(
                  Location(s"/v2/$name/blobs/uploads/$uuid"),
                  `Docker-Upload-Uuid`(uuid),
                  RawHeader("Range", "0-0")
                )))
              case uuid :: "uploads" :: "blobs" :: xs =>
                println(s"uuid: $uuid")
                val name = imageName(xs)
                ctx.complete(ctx.request.entity.toStrict(1.second).flatMap { entity =>
                  val digest = DigestUtils.sha256Hex(entity.getData.toArray)
                  val blob = imageFile(name)
                  entity.dataBytes.runWith(akka.stream.scaladsl.FileIO.toFile(blob)).map { _ =>
                    HttpResponse(StatusCodes.Accepted, headers = List(
                      Location(s"/v2/$name/blobs/$uuid"),
                      `Docker-Upload-Uuid`(java.util.UUID.fromString(uuid)),
                      `Docker-Content-Digest`("sha256", digest),
                      RawHeader("Range", s"0-${blob.length}")
                    ))
                  }
                })
              case prefix :: "blobs" :: xs =>
                val name = imageName(xs)
                val blob = imageFile(name)
                if (prefix.startsWith("sha256")) {
                  ctx.complete(HttpResponse(StatusCodes.OK,
                    headers = List(
                      `Docker-Content-Digest`("sha256", prefix)
                    ),
                    entity = HttpEntity(
                      ContentTypes.`application/octet-stream`,
                      blob.length(),
                      akka.stream.scaladsl.Source.maybe
                    )
                  ))
                } else {
                  val digest = ctx.request.uri.query().get("digest").get
                  ctx.complete(HttpResponse(StatusCodes.Created, headers = List(
                    Location(s"/v2/$name/blobs/sha256:$digest"),
                    `Docker-Content-Digest`("sha256", digest)
                  )))
                }
              case _ => ctx.complete(notFoundResponse)
            }
          }
        } ~
        path(RestPath) { _ =>
          pathEndOrSingleSlash {
            { ctx: RequestContext =>
              ctx.complete(ctx.request.entity.toStrict(1.second).map { entity =>
                println(s"unknown route: uri: ${ctx.request.uri}, headers: ${ctx.request.headers}, entity: $entity")
                notFoundResponse
              })
            }
          }
        }
      }
    }
    // format: ON
  }

  private def imageName(xs: List[String]) =
    xs.reverse.mkString("/")

  private def imageFile(imageName: String) =
    new java.io.File(s"/tmp/blobs/${imageName.replaceAll("/", "_")}")
}
