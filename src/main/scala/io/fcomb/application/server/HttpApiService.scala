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

package io.fcomb.application.server

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import io.circe.{DecodingFailure, HistoryOp, ParsingFailure}
import io.fcomb.json.models.errors.Formats.encodeErrors
import io.fcomb.models.errors.{Error, Errors}
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.server.CommonDirectives._
import org.apache.commons.lang3.exception.ExceptionUtils

final class HttpApiService(routes: Route)(implicit sys: ActorSystem, mat: Materializer)
    extends LazyLogging {
  import sys.dispatcher

  private def mapThrowable(e: Throwable) =
    e match {
      case _: ParsingException | _: Unmarshaller.UnsupportedContentTypeException =>
        (StatusCodes.UnprocessableEntity, Errors.deserialization(e.getMessage))
      case f: DecodingFailure =>
        val path = HistoryOp.opsToPath(f.history) match {
          case s if s.startsWith(".") => s.tail
          case s                      => s
        }
        (StatusCodes.UnprocessableEntity, Errors.deserialization(f.message, Some(path)))
      case f: ParsingFailure =>
        (StatusCodes.UnprocessableEntity, Errors.deserialization(f.message))
      case f =>
        val cause = Option(ExceptionUtils.getRootCause(f)).getOrElse(f)
        val msg   = Option(cause.getMessage()).getOrElse("")
        logger.error(msg, cause)
        (StatusCodes.InternalServerError, Errors.internal(msg))
    }

  private def completeError(status: StatusCode, error: Error) =
    complete((status, Errors.from(error)))

  private def handleException(e: Throwable) =
    (completeError _).tupled(mapThrowable(e))

  private def exceptionHandler = ExceptionHandler { case e => handleException(e) }

  private def rejectionHandler =
    RejectionHandler
      .newBuilder()
      .handleAll[AuthorizationFailedRejection.type] { _ =>
        completeWithStatus(StatusCodes.Unauthorized)
      }
      .handleAll[AuthorizationFailedRejection.type] { _ =>
        completeWithStatus(StatusCodes.Forbidden)
      }
      .handle {
        case r: MalformedRequestContentRejection => handleException(r.cause)
        case r: RejectionWithOptionalCause =>
          r.cause match {
            case Some(e) => handleException(e)
            case None    => completeError(StatusCodes.BadRequest, Errors.internal(r.toString))
          }
        case r => completeError(StatusCodes.BadRequest, Errors.internal(r.toString))
      }
      .handleNotFound(completeNotFound())
      .result

  private def handler =
    handleRejections(rejectionHandler) {
      handleExceptions(exceptionHandler)(routes)
    }

  def bind(port: Int, interface: String) =
    Http().bindAndHandle(handler, interface, port)
}

object HttpApiService {
  def start(port: Int, interface: String, routes: Route)(implicit sys: ActorSystem,
                                                         mat: Materializer) =
    new HttpApiService(routes).bind(port, interface)
}
