package io.fcomb.models.container

import io.fcomb.models.ModelWithAutoLongPk
import io.fcomb.models.image.Image
import java.time.LocalDateTime
import java.util.UUID

@SerialVersionUID(1L)
case class ContainerConfiguration(
  cpus: Int,
  memory: Int, // MB
  transfer: Int // GB
)

@SerialVersionUID(1L)
case class Container(
  id: Option[Long] = None,
  userId: UUID,
  name: String,
  configuration: ContainerConfiguration,
  image: Image,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
