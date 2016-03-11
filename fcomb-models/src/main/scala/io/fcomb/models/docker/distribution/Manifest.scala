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
  mediaType:     String           = MediaTypes.v2.mediaTypeManifest,
  config:        Descriptor,
  layers:        List[Descriptor]
) extends Manifest

object MediaTypes {
  object v1 {
    val manifest = "application/vnd.docker.distribution.manifest.v1+json"
    val signedManifest = "application/vnd.docker.distribution.manifest.v1+prettyjws"
    val manifestLayer = "application/vnd.docker.container.image.rootfs.diff+x-gtar"
  }

  object v2 {
    val mediaTypeManifest = "application/vnd.docker.distribution.manifest.v2+json"
    val mediaTypeConfig = "application/vnd.docker.container.image.v1+json"
    val mediaTypeLayer = "application/vnd.docker.image.rootfs.diff.tar.gzip"
  }
}
