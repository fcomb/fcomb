package io.fcomb.models.docker.distribution

import java.util.UUID

final case class ImageManifestLayer(
  imageManifestId: Long,
  layerBlobId:     UUID
)
