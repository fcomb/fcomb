package io.fcomb.models.docker.distribution

final case class ImageTagsResponse(
  name: String,
  tags: Seq[String]
)

final case class ImageManifestTag(
  imageId:         Long,
  imageManifestId: Long,
  tag:             String
)
