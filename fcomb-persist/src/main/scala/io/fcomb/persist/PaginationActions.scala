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

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.{Pagination, SortOrder}
import slick.ast.Ordering
import slick.lifted.ColumnOrdered

trait PaginationActions {
  private val unknownSortColumnPF: PartialFunction[String, Rep[_]] = {
    case column => throw new IllegalArgumentException(s"Unknown sort column: $column")
  }

  protected def sortByQuery[E, U](scope: Query[E, U, Seq], p: Pagination)(
      f: E => PartialFunction[String, Rep[_]],
      default: E => ColumnOrdered[_]) = {
    p.sort match {
      case Nil => scope.sortBy(default)
      case xs =>
        xs.foldLeft(scope) {
          case (q, (column, order)) =>
            val sortOrder = order match {
              case SortOrder.Asc  => Ordering(Ordering.Asc)
              case SortOrder.Desc => Ordering(Ordering.Desc)
            }
            q.sortBy { t =>
              val c = f(t).applyOrElse(column, unknownSortColumnPF)
              ColumnOrdered(c, sortOrder)
            }
        }
    }
  }
}
