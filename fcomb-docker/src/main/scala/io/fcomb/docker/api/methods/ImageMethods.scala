package io.fcomb.docker.api.methods

import java.time.{LocalDateTime, ZonedDateTime}

object ImageMethods {
  final case class ImageItem(
    id: String,
    parentId: Option[String],
    repoTags: List[String],
    repoDigests: List[String],
    createdAt: ZonedDateTime,
    size: Long,
    virtualSize: Long,
    labels: Map[String, String]
  ) extends DockerApiResponse
}
