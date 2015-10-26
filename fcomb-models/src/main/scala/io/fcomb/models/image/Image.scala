package io.fcomb.models.image

import io.fcomb.models.ModelWithAutoLongPk

@SerialVersionUID(1L)
case class DockerImageSettings(
  repository: String,
  tag: String,
  imageId: String
)

@SerialVersionUID(1L)
case class Image(
  id: Option[Long] = None,
  title: String, // Ubuntu ​Wily Werewolf (15.10)
  dockerSettings: DockerImageSettings,
  parentId: Option[Long] = None
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
