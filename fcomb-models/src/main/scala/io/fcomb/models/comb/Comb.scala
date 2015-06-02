package com.fcomb.models.comb

import org.joda.time.DateTime
import java.util.UUID

@SerialVersionUID(1L)
case class Comb(
  id: UUID,
  userId: UUID,
  name: Option[String],
  slug: String,
  createdAt: DateTime,
  updatedAt: DateTime
)
