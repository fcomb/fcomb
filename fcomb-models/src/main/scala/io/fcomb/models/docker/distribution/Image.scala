package io.fcomb.models.docker.distribution

import io.fcomb.models.ModelWithAutoLongPk
import java.time.ZonedDateTime

case class Image(
  id:        Option[Long]  = None,
  name:      String,
  userId:    Long,
  createdAt: ZonedDateTime,
  updatedAt: ZonedDateTime
)
    extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}

object Image {
  val nameRegEx =
    """(?:(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9])(?:(?:\.(?:[a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9-]*[a-zA-Z0-9]))+)?(?::[0-9]+)?/)?[a-z0-9]+(?:(?:(?:[._]|__|[-]*)[a-z0-9]+)+)?(?:(?:/[a-z0-9]+(?:(?:(?:[._]|__|[-]*)[a-z0-9]+)+)?)+)?""".r
}

final case class DistributionImageCatalog(
  repositories: Seq[String]
)
