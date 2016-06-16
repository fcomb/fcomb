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

package io.fcomb.server.api.user

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers.Location
import cats.data.Validated
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.circe.java8.time._
import io.fcomb.models.docker.distribution.ImageCreateRequest
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.PaginationDirectives._
import io.fcomb.server.api.{apiVersion, UserHandler}
import scala.collection.immutable

object RepositoriesHandler {
  val pathPrefix = "repositories"

  lazy val fullPrefix = s"/$apiVersion/${UserHandler.pathPrefix}/$pathPrefix/"

  def create = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        entity(as[ImageCreateRequest]) { req =>
          onSuccess(ImagesRepo.createByRequest(req, user)) {
            case Validated.Valid(res) =>
              val uri     = fullPrefix + res.getId.toString
              val headers = immutable.Seq(Location(uri))
              respondWithHeaders(headers) {
                complete((StatusCodes.Created, res))
              }
            case Validated.Invalid(e) =>
              ??? // TODO
          }
        }
      }
    }
  }

  def index = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        extractPagination { pg =>
          onSuccess(ImagesRepo.findByUserOwnerWithPagination(user.getId, pg)) { pd =>
            completePagination(pd)
          }
        }
      }
    }
  }
}
