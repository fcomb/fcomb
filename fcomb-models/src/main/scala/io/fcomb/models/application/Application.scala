package io.fcomb.models.application

import io.fcomb.models.ModelWithAutoLongPk

@SerialVersionUID(1L)
case class Application(
  id: Option[Long] = None,
  name: String,
  image: DockerImage,
  deployOptions: DockerDeployOptions
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}

sealed trait Image

sealed trait ContainerImage extends Image

@SerialVersionUID(1L)
case class DockerImage(
  name: String,
  tag: Option[String] = None,
  registry: Option[String] = None
) extends ContainerImage

sealed trait DeployOptions

sealed trait ContainerDeployOptions extends DeployOptions

case class DockerDeployPort()

@SerialVersionUID(1L)
case class DockerDeployOptions(
  ports: Set[DockerDeployPort],
  isAutoRestart: Boolean,
  isAutoDestroy: Boolean,
  isPrivileged: Boolean,
  // pid: ...,
  // network: ....
  command: Option[String],
  entrypoint: Option[String],
  memoryLimit: Option[Long],
  cpuShares: Option[Long]
) extends ContainerDeployOptions
