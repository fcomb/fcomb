package io.fcomb.models.application

import io.fcomb.models.ModelWithAutoLongPk

@SerialVersionUID(1L)
case class Application(
  id: Option[Long] = None
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}
