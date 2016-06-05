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

package io.fcomb

import java.util.UUID

package object models {
  sealed trait ModelWithPk {
    type PkType
    type IdType = Option[PkType]

    val id: IdType

    def getId() =
      id.getOrElse(
          throw new IllegalArgumentException("Column 'id' cannot be empty")
      )
  }

  trait ModelWithLongPk extends ModelWithPk {
    type PkType = Long
  }

  sealed trait ModelWithAutoPk[T] {
    def withPk(id: T): ModelWithAutoPk[T]
  }

  trait ModelWithAutoLongPk extends ModelWithLongPk with ModelWithAutoPk[Long]

  trait ModelWithUuidPk extends ModelWithPk {
    type PkType = UUID
  }

  trait ServiceModel

  final case class PaginationData(total: Int)

  final case class MultipleDataResponse[A](
      items: Seq[A],
      pagination: Option[PaginationData]
  )

  sealed trait SortOrder
  final case object Asc  extends SortOrder
  final case object Desc extends SortOrder

  final case class PaginatorQuery(
      limit: Long,
      offset: Long,
      filter: Seq[(String, String)],
      filterNot: Seq[(String, String)],
      order: Seq[(String, SortOrder)],
      includes: Seq[String],
      excludes: Seq[String]
  )
}
