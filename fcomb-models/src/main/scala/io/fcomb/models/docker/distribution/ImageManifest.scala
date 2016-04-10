package io.fcomb.models.docker.distribution

import java.time.ZonedDateTime

case class ImageManifest(
  sha256Digest: String,
  imageId:      Long,
  tags:         List[String],
  createdAt:    ZonedDateTime,
  updatedAt:    ZonedDateTime
)
