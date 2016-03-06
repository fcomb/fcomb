package io.fcomb.docker.distribution.server.api.services

import io.fcomb.api.services.{ServiceContext, ServiceMethod}
import io.fcomb.api.services.ServiceRoute._
import io.fcomb.docker.distribution.server.api.services.headers._
import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions
import java.util.UUID

object Routes {
  val apiVersion = "v2"

  private val versionHeader = `Docker-Distribution-Api-Version`("2.0")

  private val defaultHeaders = List(versionHeader)

  private val notFoundResponse = HttpResponse(StatusCodes.NotFound)

  private val uuidRegEx = """[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r

  private implicit def serviceMethod2RouteImplicit(method: ServiceMethod)(
    implicit
    ec:   ExecutionContext,
    rCtx: RequestContext
  ): Future[RouteResult] = {
    val ctx = new ServiceContext {
      val requestContext = rCtx
      val requestContentType = rCtx.request.entity.contentType
    }
    serviceResultToRoute(rCtx, method(ctx))
  }

  private def completeAsNotFound()(implicit rCtx: RequestContext) =
    rCtx.complete(notFoundResponse)

  def apply()(implicit sys: ActorSystem, mat: Materializer): Route = {
    import sys.dispatcher

    // format: OFF
    respondWithDefaultHeaders(defaultHeaders) {
      pathPrefix(apiVersion) {
        pathEndOrSingleSlash {
          get(AuthService.versionCheck())
        } ~
        path(Segments ~ Slash.?) { segments =>
          { implicit rCtx: RequestContext =>
            val method = rCtx.request.method
            println(s"image action => $method uri: ${rCtx.request.uri}, segments: $segments, headers: ${rCtx.request.headers}")
            segments.reverse match {
              case "uploads" :: "blobs" :: xs if method == HttpMethods.POST =>
                ImageService.create(imageName(xs))
              case id :: "uploads" :: "blobs" :: xs if uuidRegEx.findFirstIn(id).nonEmpty =>
                val uuid = UUID.fromString(id)
                ImageService.upload(imageName(xs), uuid)
              case digest :: "blobs" :: xs if digest.startsWith("sha256:") =>
                val image = imageName(xs)
                method match {
                  case HttpMethods.HEAD => ImageService.show(image, digest)
                  case _ => completeAsNotFound()
                }
              //   val name = imageName(xs)
              //   val blob = imageFile(name)
              //   if (prefix.startsWith("sha256")) {
              //     rCtx.complete(HttpResponse(StatusCodes.OK,
              //       headers = List(
              //         `Docker-Content-Digest`("sha256", prefix)
              //       ),
              //       entity = HttpEntity(
              //         ContentTypes.`application/octet-stream`,
              //         blob.length(),
              //         akka.stream.scaladsl.Source.maybe
              //       )
              //     ))
              //   } else {
              //     val digest = rCtx.request.uri.query().get("digest").get
              //     rCtx.complete(HttpResponse(StatusCodes.Created, headers = List(
              //       Location(s"/v2/$name/blobs/sha256:$digest"),
              //       `Docker-Content-Digest`("sha256", digest)
              //     )))
              //   }
              case _ => completeAsNotFound()
            }
          }
        } ~
        path(RestPath) { _ =>
          pathEndOrSingleSlash {
            { ctx: RequestContext =>
              import scala.concurrent.duration._
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
