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

package io.fcomb.models

final case class PaginationData[T](
    data: Seq[T],
    total: Int,
    offset: Long,
    limit: Long
)

final case class Pagination(
    limit: Long,
    offset: Long
)

object Pagination {
  val defaultLimit  = 0L
  val defaultOffset = 0L

  def apply(limitOpt: Option[Long], offsetOpt: Option[Long]): Pagination = {
    val limit = limitOpt match {
      case Some(v) if v >= 1 && v <= defaultLimit => v
      case _                                      => defaultLimit
    }
    val offset = offsetOpt match {
      case Some(v) if v >= defaultOffset => v
      case _                             => defaultOffset
    }
    Pagination(limit, offset)
  }

  lazy val withDefaults = Pagination(defaultLimit, defaultOffset)
}
