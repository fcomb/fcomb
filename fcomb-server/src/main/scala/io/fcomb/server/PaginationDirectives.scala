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
import akka.http.scaladsl.model.{ContentRange, StatusCodes}
import akka.http.scaladsl.model.headers.{`Content-Range`, Range, RangeUnits}
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.Encoder
import io.circe.generic.auto._
import io.fcomb.models.{Pagination, PaginationData}
import scala.compat.java8.OptionConverters._
import scala.collection.immutable

trait PaginationDirectives {
  def extractPagination: Directive1[Pagination] =
    optionalHeaderValueByType[Range](()).flatMap {
      case Some(Range(_, range +: _)) =>
        val p = Pagination(
          limitOpt = range.getSliceLast.asScala,
          offsetOpt = range.getOffset.asScala
        )
        provide(p)
      case _ =>
        parameters(('limit.as[Long].?, 'offset.as[Long].?)).tflatMap {
          case (limitOpt, offsetOpt) =>
            val p = Pagination(limitOpt = limitOpt, offsetOpt = offsetOpt)
            provide(p)
        }
    }

  def completePagination[T](label: String, pd: PaginationData[T])(implicit encoder: Encoder[T]) = {
    val position = pd.data.length + pd.offset
    val status   = if (position < pd.total) StatusCodes.PartialContent else StatusCodes.OK
    val range    = ContentRange(pd.offset, position, pd.total.toLong)
    val headers  = immutable.Seq(`Content-Range`(RangeUnits.Other(label), range))
    respondWithHeaders(headers) {
      complete((status, pd))
    }
  }
}

object PaginationDirectives extends PaginationDirectives
