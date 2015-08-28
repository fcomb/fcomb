package io.fcomb.models.comb

import io.fcomb.models.ModelWithAutoLongPk
import java.time.LocalDateTime
import java.util.UUID

@SerialVersionUID(1L)
case class Comb(
  id:        Option[Long] = None,
  userId:    UUID,
  name:      String,
  slug:      String,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
