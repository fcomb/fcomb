package io.fcomb.models.docker.distribution

sealed trait Manifest

case class FsLayer(
  blobSum: String
)

case class ManifestV1(
  schemaVersion: Int,
  name:     String,
  tag:      String,
  fsLayers: List[FsLayer],
  architecture: String //,
  // history: ,
  // signature: String
) extends Manifest

object MediaTypes {
  val manifestV1 = "application/vnd.docker.distribution.manifest.v1+json"
  val signedManifestV1 = "application/vnd.docker.distribution.manifest.v1+prettyjws"
	val manifestLayer = "application/vnd.docker.container.image.rootfs.diff+x-gtar"
}
