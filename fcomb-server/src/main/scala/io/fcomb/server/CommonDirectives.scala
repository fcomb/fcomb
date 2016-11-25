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

package io.fcomb.server

import akka.http.scaladsl.model.headers.{
  `If-None-Match`,
  ETag,
  EntityTag,
  EntityTagRange,
  Location
}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.circe.Encoder
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.rpc.Formats.encodeDataResponse
import io.fcomb.models.IdLens
import io.fcomb.rpc.DataResponse
import scala.collection.immutable
import scala.concurrent.Future

object CommonDirectives {
  def completeWithStatus(status: StatusCode): Route =
    complete(HttpResponse(status))

  def completeNotFound(): Route =
    completeWithStatus(StatusCodes.NotFound)

  def completeOrNotFound[T: Encoder](opt: Option[T]): Route =
    opt match {
      case Some(item) => completeWithEtag(StatusCodes.OK, item)
      case None       => completeNotFound()
    }

  def completeOrNotFound[T: Encoder](fut: => Future[Option[T]]): Route =
    onSuccess(fut)(completeOrNotFound(_))

  def completeNoContent(): Route =
    completeWithStatus(StatusCodes.NoContent)

  def completeAccepted(): Route =
    completeWithStatus(StatusCodes.Accepted)

  def completeWithEtag[T: Encoder](status: StatusCode, item: T): Route = {
    val etagHash = item.hashCode.toHexString
    optionalHeaderValueByType[`If-None-Match`](()) {
      case Some(`If-None-Match`(EntityTagRange.Default(Seq(EntityTag(`etagHash`, _))))) =>
        complete(notModified)
      case _ =>
        val headers = immutable.Seq(ETag(etagHash, false))
        respondWithHeaders(headers)(complete((status, item)))
    }
  }

  def completeData[T: Encoder](data: Seq[T]): Route =
    completeWithEtag(StatusCodes.OK, DataResponse(data))

  def completeCreated[T: Encoder](item: T)(implicit idLens: IdLens[T]): Route =
    extractMatchedPath { prefix =>
      val uri = Uri(path = prefix / idLens.get(item))
      respondWithHeader(Location(uri))(complete((StatusCodes.Created, item)))
    }

  private val notModified = HttpResponse(StatusCodes.NotModified)
}
