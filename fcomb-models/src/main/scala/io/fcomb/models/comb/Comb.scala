package com.fcomb.models.comb

import java.time.LocalDateTime
import java.util.UUID

@SerialVersionUID(1L)
case class Comb(
  id: UUID,
  userId: UUID,
  name: Option[String],
  slug: String,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
)
