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

sealed trait SortOrder

object SortOrder {
  final case object Asc  extends SortOrder
  final case object Desc extends SortOrder
}

final case class PaginationData[T](
    data: Seq[T],
    total: Int,
    offset: Long,
    limit: Long
)

final case class Pagination(
    // filter: List[(String, String)],
    // filterNot: List[(String, String)],
    sort: List[(String, SortOrder)],
    limit: Long,
    offset: Long
)

object Pagination {
  val defaultLimit  = 64L
  val defaultOffset = 0L

  def apply(sortOpt: Option[String], limitOpt: Option[Long], offsetOpt: Option[Long]): Pagination = {
    val limit = limitOpt match {
      case Some(v) if v >= 1 && v <= defaultLimit => v
      case _                                      => defaultLimit
    }
    val offset = offsetOpt match {
      case Some(v) if v >= defaultOffset => v
      case _                             => defaultOffset
    }
    val sort = sortOpt match {
      case Some(s) if s.nonEmpty =>
        s.split(',').toList.map {
          case column if column.startsWith("-") => (column.drop(1), SortOrder.Desc)
          case column                           => (column, SortOrder.Asc)
        }
      case _ => Nil
    }
    Pagination(sort, limit, offset)
  }
}