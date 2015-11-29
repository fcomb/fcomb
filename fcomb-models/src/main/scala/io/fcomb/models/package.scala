package io.fcomb

import java.util.UUID

package object models {
  sealed trait ModelWithPk[PK] {
    type PkType = PK
    type IdType = Option[PK]

    val id: IdType

    def getId() =
      id.getOrElse(throw new IllegalArgumentException("Column 'id' cannot be empty"))
  }

  trait ModelWithLongPk extends ModelWithPk[Long]

  sealed trait ModelWithAutoPk[T] {
    def withPk(id: T): ModelWithAutoPk[T]
  }

  trait ModelWithAutoLongPk extends ModelWithLongPk with ModelWithAutoPk[Long]

  trait ModelWithUuidPk extends ModelWithPk[UUID]

  trait ServiceModel

  case class PaginationData(
    total: Int,
    offset: Long,
    limit: Long
  )

  case class MultipleDataResponse[A](
    items: Seq[A],
    pagination: Option[PaginationData]
  )

  sealed trait SortOrder
  case object Asc extends SortOrder
  case object Desc extends SortOrder

  case class PaginatorQuery(
    limit: Long,
    offset: Long,
    filter: Seq[(String, String)],
    filterNot: Seq[(String, String)],
    order: Seq[(String, SortOrder)],
    includes: Seq[String],
    excludes: Seq[String]
  )
}
