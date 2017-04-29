/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.fcomb.docker.distribution.server.api._
import io.fcomb.docker.distribution.server.headers._
import io.fcomb.docker.distribution.server.PathMatchers._
import io.fcomb.models.docker.distribution.ImageManifest
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.headers._

object Api {
  val apiVersion = "v2"

  def routes()(implicit config: ApiHandlerConfig): Route =
    // format: off
    pathPrefix(apiVersion) {
      respondWithDefaultHeaders(defaultHeaders) {
        pathEndOrSingleSlash(get(AuthenticationHandler.versionCheck())) ~
        path("_catalog")(get(ImagesHandler.catalog())) ~
        pathPrefix(Segments(2)) { xs =>
          val name = xs.mkString("/")
          pathPrefix("blobs") {
            pathPrefix("uploads") {
              pathEndOrSingleSlash(post(ImageBlobUploadsHandler.create(name))) ~
              path(JavaUUID) { id =>
                put(ImageBlobUploadsHandler.uploadComplete(name, id)) ~
                patch(ImageBlobUploadsHandler.uploadChunk(name, id)) ~
                delete(ImageBlobUploadsHandler.destroy(name, id))
              }
            } ~
            path(JavaUUID)(id => put(ImageBlobUploadsHandler.uploadComplete(name, id))) ~
            path(ImageManifest.sha256Prefix ~ Segment) { digest =>
              head(ImageBlobsHandler.show(name, digest)) ~
              get(ImageBlobsHandler.download(name, digest)) ~
              delete(ImageBlobsHandler.destroy(name, digest))
            }
          } ~
          path("manifests" / ReferencePath) { reference =>
            get(ImagesHandler.getManifest(name, reference)) ~
            put(ImagesHandler.uploadManifest(name, reference)) ~
            delete(ImagesHandler.destroyManifest(name, reference))
          } ~
          path("tags" / "list")(get(ImagesHandler.tags(name)))
        }
      }
    }
    // format: on

  private val versionHeader = `Docker-Distribution-Api-Version`("2.0")

  val defaultHeaders = List(
    versionHeader,
    `X-Content-Type-Options`("nosniff"),
    `X-Frame-Options`("sameorigin"),
    `X-XSS-Protection`("1; mode=block")
  )
}
