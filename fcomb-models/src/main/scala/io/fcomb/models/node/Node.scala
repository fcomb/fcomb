package io.fcomb.models.node

import io.fcomb.models.ModelWithAutoLongPk
import java.time.ZonedDateTime

object NodeState extends Enumeration {
  type NodeState = Value

  val Initialize = Value("initialize")
  val Available = Value("available")
}

case class Node(
  id: Option[Long] = None,
  state: NodeState.NodeState,
  token: String, // security token specific for node
  rootCertificateId: Long,
  signedCertificate: Array[Byte],
  publicKeySha256: String,
  createdAt: ZonedDateTime,
  updatedAt: ZonedDateTime
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
