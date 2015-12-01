package io.fcomb.models.application

import io.fcomb.models.ModelWithAutoLongPk

@SerialVersionUID(1L)
case class Application(
  id: Option[Long] = None,
  name: String,
  image: ContainerImage,
  deployOptions: ContainerDeployOptions
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

@SerialVersionUID(1L)
case class ContainerDeployOptions(
) extends DeployOptions
