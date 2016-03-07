package io.fcomb.models.docker.distribution

case class FsLayer(
  blobSum: String
)

case class Manifest(
  name:     String,
  tag:      String,
  fsLayers: List[FsLayer],
  // history: ,
  signature: String
)
