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
}
