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
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.fcomb.docker.distribution.server.api._
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.models.docker.distribution.{Reference, ImageManifest}
import io.fcomb.server.headers._

object Routes {
  val apiVersion = "v2"

  def apply(): Route = {
    // format: OFF
    pathPrefix(apiVersion) {
      respondWithDefaultHeaders(defaultHeaders) {
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
  }

  private val versionHeader = `Docker-Distribution-Api-Version`("2.0")

  val defaultHeaders = List(
    versionHeader,
    `X-Content-Type-Options`("nosniff"),
    `X-Frame-Options`("sameorigin"),
    `X-XSS-Protection`("1; mode=block")
  )
}
