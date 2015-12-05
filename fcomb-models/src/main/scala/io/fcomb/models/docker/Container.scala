package io.fcomb.models.docker

import io.fcomb.models.ModelWithAutoLongPk

sealed trait Container {
  val id: Option[Long]
}

case class DockerContainer(
  id: Option[Long] = None
) extends Container with ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
