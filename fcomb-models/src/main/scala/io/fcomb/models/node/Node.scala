package io.fcomb.models.node

import io.fcomb.models.ModelWithAutoLongPk
import java.time.ZonedDateTime
import java.net.InetAddress
import cats.Eq

object NodeState extends Enumeration {
  type NodeState = Value

  val Pending = Value("pending")
  val Available = Value("available")
  val Unreachable = Value("unreachable")
  val Upgrading = Value("upgrading")
  val Terminating = Value("terminating")
  val Terminated = Value("terminated")

  implicit val nodeStateEq: Eq[NodeState] =
    Eq.fromUniversalEquals
}

case class Node(
    id:                Option[Long]          = None,
    userId:            Long,
    state:             NodeState.NodeState,
    token:             String, // security token specific for node
    rootCertificateId: Long,
    signedCertificate: Array[Byte],
    publicKeyHash:     String,
    publicIpAddress:   Option[String]        = None,
    createdAt:         ZonedDateTime,
    updatedAt:         ZonedDateTime,
    terminatedAt:      Option[ZonedDateTime] = None
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))

  def publicIpInetAddress() =
    publicIpAddress.map(InetAddress.getByName)
}
