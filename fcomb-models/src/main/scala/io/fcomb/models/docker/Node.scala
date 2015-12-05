package io.fcomb.models.docker

import io.fcomb.models.ModelWithAutoLongPk

sealed trait Node {
  val id: Option[Long]
}

case class DockerNode(
  id: Option[Long] = None
) extends Node with ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
