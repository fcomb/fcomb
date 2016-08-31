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

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.{ContentRange, StatusCodes, HttpRequest}
import akka.http.scaladsl.model.headers.{`Content-Range`, Range, RangeUnits}
import io.fcomb.server.CirceSupport._
import io.circe.Encoder
import io.fcomb.models.{Pagination, PaginationData}
import io.fcomb.json.models.Formats._
import scala.compat.java8.OptionConverters._
import scala.collection.immutable

object PaginationDirectives {
  def extractPagination: Directive1[Pagination] = {
    extractRequest.flatMap { req =>
      optionalHeaderValueByType[Range](()).flatMap {
        case Some(Range(_, range +: _)) =>
          parameter('sort.?).flatMap { sortOpt =>
            val p = Pagination.wrap(sortOpt,
                                    parseFilter(req),
                                    range.getSliceLast.asScala,
                                    range.getOffset.asScala)
            provide(p)
          }
        case _ =>
          parameters(('sort.?, 'limit.as[Long].?, 'offset.as[Long].?)).tflatMap {
            case (sortOpt, limitOpt, offsetOpt) =>
              val p = Pagination.wrap(sortOpt, parseFilter(req), limitOpt, offsetOpt)
              provide(p)
          }
      }
    }
  }

  private def parseFilter(req: HttpRequest): immutable.Map[String, String] = {
    req.uri
      .query()
      .filterNot {
        case ("" | "sort" | "limit" | "offset" | "token", _) => true
        case (_, value)                                      => value.isEmpty
      }
      .toMap
  }

  // TODO: add Link: <api?limit=&offset=>; rel="next", <api?limit=&offset=>; rel="last"
  def completePagination[T](label: String, pd: PaginationData[T])(implicit encoder: Encoder[T]) = {
    val position = pd.data.length + pd.offset - 1L
    val (status, headers) =
      if (pd.offset == 0L && position < pd.total) (StatusCodes.OK, immutable.Seq.empty)
      else if (pd.total != 0 && pd.offset >= pd.total)
        (StatusCodes.RequestedRangeNotSatisfiable, immutable.Seq.empty)
      else {
        val range = ContentRange(pd.offset, position, pd.total.toLong)
        val xs    = immutable.Seq(`Content-Range`(RangeUnits.Other(label), range))
        (StatusCodes.PartialContent, xs)
      }
    respondWithHeaders(headers) {
      complete((status, pd))
    }
  }
}
