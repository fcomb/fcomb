package io.fcomb

import java.util.UUID

package object models {
  sealed trait ModelWithPk[PK, ID] {
    type PkType = PK
    type IdType = ID

    val id: PK

    def idOpt: Option[ID]
  }

  trait ModelWithId extends ModelWithPk[Option[Long], Long] {
    val id: Option[Long]

    def idOpt = id

    def withId[T <: ModelWithId](id: Long): T
  }

  trait ModelWithUuid extends ModelWithPk[UUID, UUID] {
    val id: UUID

    def idOpt = Some(id)
  }
}
