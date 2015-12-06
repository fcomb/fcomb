package io.fcomb.models

import java.time.ZonedDateTime

object UserCertificateKind extends Enumeration {
  type UserCertificateKind = Value

  val Root = Value("root")
  val Client = Value("client")
}

@SerialVersionUID(1L)
case class UserCertificate(
  userId: Long,
  kind: UserCertificateKind.UserCertificateKind,
  certificate: Array[Byte],
  key: Array[Byte],
  password: Option[Array[Byte]],
  createdAt: ZonedDateTime
)
