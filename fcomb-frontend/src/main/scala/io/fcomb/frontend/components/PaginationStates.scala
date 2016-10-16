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

package io.fcomb.frontend.components

import io.fcomb.models.SortOrder

final case class PaginationOrderState(page: Int,
                                      total: Int,
                                      sortColumn: String,
                                      sortOrder: SortOrder)

final case class PaginationState(page: Int, total: Int)

object PaginationOrderState {
  def apply(sortColumn: String, sortOrder: SortOrder = SortOrder.Asc): PaginationOrderState =
    PaginationOrderState(1, 0, sortColumn, sortOrder)
}
