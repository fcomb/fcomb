package io.fcomb.docker.distribution.server.api.services

import io.fcomb.api.services.{ServiceContext, ServiceMethod}
import io.fcomb.api.services.ServiceRoute._
import io.fcomb.api.services.headers._
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

  def apply()(implicit sys: ActorSystem, mat: Materializer): Route = {
    import sys.dispatcher

    // format: OFF
    respondWithDefaultHeaders(defaultHeaders) {
      pathPrefix(apiVersion) {
        pathEndOrSingleSlash {
          get(AuthService.versionCheck())
        } ~
        pathPrefix("_catalog") {
          pathEndOrSingleSlash {
            get { implicit ctx: RequestContext => // TODO
              ImageService.catalog
            }
          }
        } ~
        path(Segments ~ Slash.?) { segments => implicit ctx: RequestContext =>
          val method = ctx.request.method
          println(s"image action => $method uri: ${ctx.request.uri}, segments: $segments, headers: ${ctx.request.headers}")
          segments.reverse match {
            case "uploads" :: "blobs" :: xs if method == HttpMethods.POST =>
              ctx.request.uri.query().get("digest") match {
                case Some(digest) => ImageService.createBlob(imageName(xs), digest)
                case None => ImageService.createBlobUpload(imageName(xs))
              }
            case id :: "uploads" :: "blobs" :: xs if uuidRegEx.findFirstIn(id).nonEmpty =>
              val uuid = UUID.fromString(id)
              method match {
                case HttpMethods.PUT =>
                  ImageService.uploadBlob(imageName(xs), uuid)
                case HttpMethods.PATCH =>
                  ImageService.uploadBlobChunk(imageName(xs), uuid)
                case HttpMethods.DELETE =>
                  ImageService.destroyBlobUpload(imageName(xs), uuid)
                case _ => completeAsNotFound()
              }
            case id :: "blobs" :: xs =>
              val image = imageName(xs)
              println(ctx.request)
              method match {
                case HttpMethods.HEAD if id.startsWith("sha256:") =>
                  println(s"HEAD")
                  ImageService.showBlob(image, id)
                case HttpMethods.GET if id.startsWith("sha256:") =>
                  println(s"GET")
                  ImageService.downloadBlob(image, id)
                case HttpMethods.PUT =>
                  ImageService.uploadComplete(image, UUID.fromString(id))
                case HttpMethods.DELETE if id.startsWith("sha256:") =>
                  ImageService.destroyBlob(imageName(xs), id)
                case _ => completeAsNotFound()
              }
            case reference :: "manifests" :: xs =>
              val image = imageName(xs)
              method match {
                case HttpMethods.GET =>
                  ImageService.getManifest(image, reference)
                case HttpMethods.PUT =>
                  ImageService.uploadManifest(image, reference)
                case HttpMethods.DELETE =>
                  ImageService.destroyManifest(image, reference)
                case _ => completeAsNotFound()
              }
            case _ => completeAsNotFound()
          }
        } ~
        path(RestPath) { _ =>
          pathEndOrSingleSlash { ctx: RequestContext =>
            import scala.concurrent.duration._
            ctx.complete(ctx.request.entity.toStrict(1.second).map { entity =>
              println(s"unknown route: uri: ${ctx.request.uri}, headers: ${ctx.request.headers}, entity: $entity")
              notFoundResponse
            })
          }
        }
      }
    }
    // format: ON
  }

  private val versionHeader = `Docker-Distribution-Api-Version`("2.0")

  private val defaultHeaders = List(
    versionHeader,
    `X-Content-Type-Options`("nosniff"),
    `X-Frame-Options`("sameorigin"),
    `X-XSS-Protection`("1; mode=block")
  )

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

  private def imageName(xs: List[String]) =
    xs.reverse.mkString("/")
}
