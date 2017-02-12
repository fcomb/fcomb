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

package io.fcomb.persist

import io.fcomb.PostgresProfile.api._
import io.fcomb.models.common.{Enum, EnumItem}
import io.fcomb.models.Pagination

object Filters {
  def filter[E, U](query: Query[E, U, Seq], filters: Map[String, String])(
      f: (E, String) => PartialFunction[String, Rep[Boolean]]): Query[E, U, Seq] =
    filters.foldLeft(query) {
      case (q, (column, value)) =>
        q.filter(t => f(t, value).orElse(defaultFilter).apply(column))
    }

  def filter[E, U](query: Query[E, U, Seq], p: Pagination)(
      f: (E, String) => PartialFunction[String, Rep[Boolean]]): Query[E, U, Seq] =
    filter(query, p.filter)(f)

  private val defaultFilter: PartialFunction[String, Rep[Boolean]] = {
    case _ => LiteralColumn(false)
  }

  def filterByMask(q: Rep[String], value: String): Rep[Boolean] =
    q.like(parseMask(value))

  def filterCitextByMask(q: Rep[String], value: String): Rep[Boolean] =
    q.like(parseMask(value).asColumnOfType[String]("citext"))

  def filterByEnum[T <: EnumItem: BaseColumnType](q: Rep[T],
                                                  enum: Enum[T],
                                                  value: String): Rep[Boolean] =
    if (value.contains(',')) q.inSetBind(value.split(',').map(enum.withName))
    else q === enum.withName(value)

  private def parseMask(value: String): String =
    value.replaceFirst("\\*", "%")
}
