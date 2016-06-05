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

package io.fcomb.server.api

import akka.http.scaladsl.model.StatusCodes
import io.fcomb.persist.SessionsRepo
import io.fcomb.models.SessionCreateRequest
import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import cats.data.Xor
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.OAuth2BearerToken

object SessionHandler {
  def create =
    extractExecutionContext { implicit ec =>
      entity(as[SessionCreateRequest]) { req =>
        onSuccess(SessionsRepo.create(req)) {
          case Xor.Right(s) => complete(StatusCodes.OK, s)
          case Xor.Left(e)  => complete(StatusCodes.BadRequest, e)
        }
      }
    }

  def destroy =
    extractExecutionContext { implicit ec =>
      extractCredentials {
        case Some(OAuth2BearerToken(token)) =>
          onSuccess(SessionsRepo.destroy(token)) { _ =>
            complete(HttpResponse(StatusCodes.NoContent))
          }
        case Some(_) => complete(HttpResponse(StatusCodes.BadRequest))
        case None    => complete(HttpResponse(StatusCodes.Unauthorized))
      }
    }
}
