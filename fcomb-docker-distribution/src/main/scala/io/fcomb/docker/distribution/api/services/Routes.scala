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
            ctx.complete(ctx.request.entity.toStrict(1.second).map { entity =>
              println(s"image action => $method uri: ${ctx.request.uri}, segments: $segments, headers: ${ctx.request.headers}, entity: ${entity.getData.length}")
              segments.reverse match {
                case "uploads" :: "blobs" :: xs =>
                  val imageName = xs.reverse.mkString("/")
                  val uuid = java.util.UUID.randomUUID()
                  HttpResponse(StatusCodes.Accepted, headers = List(
                    Location(s"/v2/$imageName/blobs/uploads/$uuid"),
                    `Docker-Upload-Uuid`(uuid),
                    RawHeader("Range", s"0-${entity.getData.length}")
                  ))
                case uuid :: "uploads" :: "blobs" :: xs =>
                  println(s"uuid: $uuid")
                  val imageName = xs.reverse.mkString("/")
                  val digest = DigestUtils.sha256Hex(entity.getData.toArray)
                  HttpResponse(StatusCodes.Accepted, headers = List(
                    // Location(s"/v2/$imageName/blobs/sha256:$digest"),
                    Location(s"/v2/$imageName/blobs/$uuid"),
                    `Docker-Upload-Uuid`(java.util.UUID.fromString(uuid)),
                    `Docker-Content-Digest`("sha256", digest),
                    RawHeader("Range", s"0-${entity.getData.length}")
                  ))
                case prefix :: "blobs" :: xs =>
                  val imageName = xs.reverse.mkString("/")
                  if (prefix.startsWith("sha256")) {
                    HttpResponse(StatusCodes.OK,
                      headers = List(
                        `Docker-Content-Digest`("sha256", prefix)
                      ),
                      entity = HttpEntity(
                        ContentTypes.`application/octet-stream`,
                        12345,
                        akka.stream.scaladsl.Source.maybe
                      )
                    )
                  } else {
                    val digest = ctx.request.uri.query().get("digest").get
                    HttpResponse(StatusCodes.Created, headers = List(
                      Location(s"/v2/$imageName/blobs/sha256:$digest"),
                      `Docker-Content-Digest`("sha256", digest)
                    ))
                  }
                case _ => notFoundResponse
              }
            })
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
}
