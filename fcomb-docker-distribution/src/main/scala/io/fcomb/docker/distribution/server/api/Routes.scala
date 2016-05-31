package io.fcomb.docker.distribution.server.api

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.`WWW-Authenticate`
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import io.fcomb.api.services.headers._
import io.fcomb.docker.distribution.server.api.headers._
import io.fcomb.models.docker.distribution.Reference
import java.util.UUID
import org.slf4j.LoggerFactory
import io.fcomb.utils.Config.docker.distribution.realm

object Routes {
  val apiVersion = "v2"

  def apply()(implicit sys: ActorSystem, mat: Materializer): Route = {
    // format: OFF
    val routes = respondWithDefaultHeaders(defaultHeaders) {
      pathSingleSlash {
        get(complete(HttpResponse(StatusCodes.OK)))
      } ~
      pathPrefix(apiVersion) {
        pathEndOrSingleSlash {
          get(AuthenticationService.versionCheck)
        } ~
        pathPrefix("_catalog") {
          pathEndOrSingleSlash {
            get(ImageService.catalog)
          }
        } ~
        pathPrefix(Segments(2, 32)) { segments =>
          pathEndOrSingleSlash {
            extractRequest { implicit req ⇒
              val method = req.method
              segments.reverse match {
                case "uploads" :: "blobs" :: xs if method == HttpMethods.POST =>
                  ImageBlobUploadService.createBlob(imageName(xs))
                case id :: "uploads" :: "blobs" :: xs if isUuid(id) =>
                  val uuid = UUID.fromString(id)
                  val image = imageName(xs)
                  method match {
                    case HttpMethods.PUT =>
                      ImageBlobUploadService.uploadComplete(image, uuid)
                    case HttpMethods.PATCH =>
                      ImageBlobUploadService.uploadBlobChunk(image, uuid)
                    case HttpMethods.DELETE =>
                      ImageBlobUploadService.destroyBlobUpload(image, uuid)
                    case _ => complete(notFoundResponse)
                  }
                case id :: "blobs" :: xs if Reference.isDigest(id) =>
                  val image = imageName(xs)
                  method match {
                    case HttpMethods.HEAD =>
                      ImageBlobService.showBlob(image, id)
                    case HttpMethods.GET =>
                      ImageBlobService.downloadBlob(image, id)
                    case HttpMethods.DELETE =>
                      ImageBlobService.destroyBlob(image, id)
                    case _ => complete(notFoundResponse)
                  }
                case ref :: "manifests" :: xs =>
                  val image = imageName(xs)
                  val reference = Reference.apply(ref)
                  method match {
                    case HttpMethods.GET =>
                      ImageService.getManifest(image, reference)
                    case HttpMethods.PUT =>
                      ImageService.uploadManifest(image, reference)
                    case HttpMethods.DELETE =>
                      ImageService.destroyManifest(image, reference)
                    case _ => complete(notFoundResponse)
                  }
                case "list" :: "tags" :: xs if method == HttpMethods.GET =>
                  ImageService.tags(imageName(xs))
                case _ => complete(notFoundResponse)
              }
            }
          }
        }
      }
    }
    // format: ON

    import de.heikoseeberger.akkahttpcirce.CirceSupport._
    import io.fcomb.json.docker.distribution.Formats._
    import io.circe.generic.auto._
    import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}

    val exceptionHandler = ExceptionHandler {
      case e ⇒
        e.printStackTrace()
        logger.error(e.getMessage(), e.getCause())
        respondWithHeaders(defaultHeaders) {
          complete(
            StatusCodes.InternalServerError,
            DistributionErrorResponse.from(DistributionError.Unknown())
          )
        }
    }
    val rejectionHandler = RejectionHandler
      .newBuilder()
      .handleAll[AuthorizationFailedRejection.type] { _ ⇒
        respondWithHeaders(defaultAuthenticateHeaders) {
          complete(
            StatusCodes.Unauthorized,
            DistributionErrorResponse.from(DistributionError.Unauthorized())
          )
        }
      }
      .handleNotFound {
        complete(HttpResponse(StatusCodes.NotFound))
      }
      .result

    handleRejections(rejectionHandler) {
      handleExceptions(exceptionHandler)(routes)
    }
  }

  private val logger = LoggerFactory.getLogger(getClass)

  private val versionHeader = `Docker-Distribution-Api-Version`("2.0")

  private val defaultHeaders = List(
    versionHeader,
    `X-Content-Type-Options`("nosniff"),
    `X-Frame-Options`("sameorigin"),
    `X-XSS-Protection`("1; mode=block")
  )

  private val authenticateHeader = `WWW-Authenticate`(challengeFor(realm))

  private val defaultAuthenticateHeaders = authenticateHeader :: defaultHeaders

  private val notFoundResponse = HttpResponse(StatusCodes.NotFound)

  private val uuidRegEx =
    """[\da-fA-F]{8}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{4}-[\da-fA-F]{12}""".r

  private def isUuid(s: String): Boolean =
    uuidRegEx.findFirstIn(s).nonEmpty

  private def imageName(xs: List[String]) =
    xs.reverse.mkString("/")
}
