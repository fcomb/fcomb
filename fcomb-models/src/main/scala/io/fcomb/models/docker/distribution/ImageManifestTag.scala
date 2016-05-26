package io.fcomb.models.docker.distribution

final case class ImageManifestTag(
  imageId:         Long,
  imageManifestId: Long,
  tag:             String
)
