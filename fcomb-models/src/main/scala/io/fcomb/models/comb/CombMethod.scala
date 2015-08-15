package io.fcomb.models.comb

import io.fcomb.models.ModelWithUuid
import java.time.LocalDateTime
import java.util.UUID

object MethodKind extends Enumeration {
  type MethodKind = Value

  val GET = Value("GET")
  val POST = Value("POST")
  val PUT = Value("PUT")
  val PATCH = Value("PATCH")
  val DELETE = Value("DELETE")
  val HEAD = Value("HEAD")
  val OPTIONS = Value("OPTIONS")
}

case class CombMethod(
  id: UUID,
  combId: UUID,
  kind: MethodKind.MethodKind,
  uri: String,
  endpoint: String,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
) extends ModelWithUuid
