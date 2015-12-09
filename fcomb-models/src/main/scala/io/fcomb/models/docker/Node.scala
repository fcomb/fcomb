package io.fcomb.models.docker

import io.fcomb.models.ModelWithAutoLongPk

object NodeState extends Enumeration {
  type NodeState = Value

  val Initialize = Value("initialize")
  val Available = Value("available")
}

sealed trait Node {
  val id: Option[Long]
  val state: NodeState.NodeState
}

case class DockerNode(
  id: Option[Long] = None,
  state: NodeState.NodeState
) extends Node with ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
