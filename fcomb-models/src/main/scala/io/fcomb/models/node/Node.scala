package io.fcomb.models.node

import io.fcomb.models.ModelWithAutoLongPk
import java.time.ZonedDateTime

object NodeState extends Enumeration {
  type NodeState = Value

  val Initializing = Value("initializing")
  val Available = Value("available")
  val Unreachable = Value("unreachable")
  val Upgrading = Value("upgrading")
  val Terminating = Value("terminating")
  val Terminated = Value("terminated")
  val Deleted = Value("deleted")
}

case class Node(
    id:                Option[Long]        = None,
    userId:            Long,
    state:             NodeState.NodeState,
    token:             String, // security token specific for node
    rootCertificateId: Long,
    signedCertificate: Array[Byte],
    publicKeyHash:     String,
    createdAt:         ZonedDateTime,
    updatedAt:         ZonedDateTime
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
