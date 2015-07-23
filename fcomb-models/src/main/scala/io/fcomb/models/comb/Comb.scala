package io.fcomb.models.comb

import io.fcomb.models.ModelWithUuid
import java.time.LocalDateTime
import java.util.UUID

@SerialVersionUID(1L)
case class Comb(
  id:        UUID,
  userId:    UUID,
  name:      String,
  slug:      String,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
) extends ModelWithUuid
