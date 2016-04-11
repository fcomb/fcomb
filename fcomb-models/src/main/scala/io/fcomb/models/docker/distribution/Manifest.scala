package io.fcomb.models.docker.distribution

sealed trait Manifest {
  val schemaVersion: Int
}

case class FsLayer(
  blobSum: String
)

case class ManifestV1(
  name:         String,
  tag:          String,
  fsLayers:     List[FsLayer],
  architecture: String,
  // history: ,
  // signature: String
  schemaVersion: Int = 1
) extends Manifest

case class Descriptor(
  mediaType: String,
  size:      Long,
  digest:    String
)

case class ManifestV2(
  schemaVersion: Int              = 2,
  mediaType:     String           = "application/vnd.docker.distribution.manifest.v2+json",
  config:        Descriptor,
  layers:        List[Descriptor]
) extends Manifest
