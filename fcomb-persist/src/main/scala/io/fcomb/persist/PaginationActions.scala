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

package io.fcomb.persist

import cats.syntax.eq._
import io.fcomb.PostgresProfile.api._
import io.fcomb.models.{Pagination, SortOrder}
import io.fcomb.models.errors.FcombException
import slick.ast.Ordering
import slick.lifted.ColumnOrdered

final case class UnknownSortColumnException(message: String) extends FcombException

object PaginationActions {
  def sort[E, U](scope: Query[E, U, Seq], order: List[(String, SortOrder)])(
      f: E => PartialFunction[String, Rep[_]],
      default: E => ColumnOrdered[_]) =
    order match {
      case Nil => scope.sortBy(default)
      case xs =>
        xs.foldRight(scope) {
          case ((column, order), q) =>
            val sortOrder = if (order === SortOrder.Asc) Ordering.Asc else Ordering.Desc
            q.sortBy { t =>
              val sortColumn = f(t).applyOrElse(column, unknownSortColumnPF)
              ColumnOrdered(sortColumn, Ordering(sortOrder))
            }
        }
    }

  def sortPaginate[E, U](scope: Query[E, U, Seq], p: Pagination)(
      f: E => PartialFunction[String, Rep[_]],
      default: E => ColumnOrdered[_]) =
    sort(paginate(scope, p), p.sort)(f, default)

  def paginate[E, U](scope: Query[E, U, Seq], p: Pagination): Query[E, U, Seq] =
    scope.drop(p.offset).take(p.limit)

  private val unknownSortColumnPF: PartialFunction[String, Rep[_]] = {
    case column => throw UnknownSortColumnException(s"Unknown sort column: $column")
  }
}
