package io.fcomb

import java.util.UUID

package object models {
  sealed trait ModelWithPk[PK, ID] {
    val id: PK

    def getId: ID
  }

  trait ModelWithId extends ModelWithPk[Option[Long], Long] {
    val id: Option[Long]

    def getId: Long = id.get

    def withId[T <: ModelWithId](id: Long): T
  }

  trait ModelWithUuid extends ModelWithPk[UUID, UUID] {
    val id: UUID

    def getId: UUID = id
  }
}
