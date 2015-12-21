package io.fcomb.models

import java.time.ZonedDateTime

object CertificateKind extends Enumeration {
  type CertificateKind = Value

  val Root = Value("root")
  val Client = Value("client")
}

@SerialVersionUID(1L)
case class UserCertificate(
  userId: Long,
  kind: CertificateKind.CertificateKind,
  certificate: Array[Byte],
  key: Array[Byte],
  createdAt: ZonedDateTime,
  updatedAt: ZonedDateTime
)
