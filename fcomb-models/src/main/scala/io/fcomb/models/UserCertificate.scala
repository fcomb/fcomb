package io.fcomb.models

import java.time.ZonedDateTime

@SerialVersionUID(1L)
case class UserCertificate(
  userId: Long,
  certificate: Array[Byte],
  key: Array[Byte],
  password: Array[Byte],
  createdAt: ZonedDateTime
)
