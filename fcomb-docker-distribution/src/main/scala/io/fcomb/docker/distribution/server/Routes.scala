/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.docker.distribution.server

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{HttpChallenges, `WWW-Authenticate`}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.fcomb.server.CirceSupport._
import io.circe.generic.auto._
import io.fcomb.docker.distribution.server.api._
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.json.models.docker.distribution.CompatibleFormats._
import io.fcomb.models.docker.distribution.{Reference, ImageManifest}
import io.fcomb.models.errors.docker.distribution.{DistributionError, DistributionErrorResponse}
import io.fcomb.server.headers._
import io.fcomb.utils.Config.docker.distribution.realm
import org.slf4j.LoggerFactory

object Routes {
  val apiVersion = "v2"

  def apply(): Route = {
    // format: OFF
    val routes =
      respondWithDefaultHeaders(defaultHeaders) {
        pathSingleSlash {
          get(complete(HttpResponse(StatusCodes.OK)))
        } ~
        pathPrefix(apiVersion) {
          pathEndOrSingleSlash {
            get(AuthenticationHandler.versionCheck)
          } ~
          path("_catalog") {
            get(ImagesHandler.catalog)
          } ~
          pathPrefix(Segments(2)) { xs =>
            val name = xs.mkString("/")
            extractRequest { implicit req =>
              pathPrefix("blobs") {
                pathPrefix("uploads") {
                  pathEndOrSingleSlash {
                    post(ImageBlobUploadsHandler.createBlob(name))
                  } ~
                  path(JavaUUID) { id =>
                    put(ImageBlobUploadsHandler.uploadComplete(name, id)) ~
                    patch(ImageBlobUploadsHandler.uploadBlobChunk(name, id)) ~
                    delete(ImageBlobUploadsHandler.destroyBlobUpload(name, id))
                  }
                } ~
                path(JavaUUID) { id =>
                  put(ImageBlobUploadsHandler.uploadComplete(name, id))
                } ~
                path(ImageManifest.sha256Prefix ~ Segment) { digest =>
                  head(ImageBlobsHandler.showBlob(name, digest)) ~
                  get(ImageBlobsHandler.downloadBlob(name, digest)) ~
                  delete(ImageBlobsHandler.destroyBlob(name, digest))
                }
              } ~
              path("manifests" / Segment) { ref =>
                val reference = Reference.apply(ref)
                get(ImagesHandler.getManifest(name, reference)) ~
                put(ImagesHandler.uploadManifest(name, reference)) ~
                delete(ImagesHandler.destroyManifest(name, reference))
              } ~
              path("tags" / "list") {
                get(ImagesHandler.tags(name))
              }
            }
          }
        }
      }
    // format: ON

    val exceptionHandler = ExceptionHandler {
      case e =>
        e.printStackTrace()
        logger.error(e.getMessage(), e.getCause())
        respondWithHeaders(defaultHeaders) {
          complete(
            (
              StatusCodes.InternalServerError,
              DistributionErrorResponse.from(DistributionError.Unknown())
            ))
        }
    }
    val rejectionHandler = RejectionHandler
      .newBuilder()
      .handleAll[AuthorizationFailedRejection.type] { _ =>
        respondWithHeaders(defaultAuthenticateHeaders) {
          complete(
            (
              StatusCodes.Unauthorized,
              DistributionErrorResponse.from(DistributionError.Unauthorized())
            ))
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

  private val authenticateHeader = `WWW-Authenticate`(HttpChallenges.basic(realm))

  private val defaultAuthenticateHeaders = authenticateHeader :: defaultHeaders
}
