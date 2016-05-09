package io.fcomb.models.docker.distribution

sealed trait Manifest {
  val schemaVersion: Int
}

final case class FsLayer(
  blobSum: String
)

final case class SignatureHeader(
  jwk: Map[String, String],
  alg: String
)

final case class Signature(
  header:      SignatureHeader,
  signature:   String,
  `protected`: String
)

final case class V1Compatibility(
  v1Compatibility: String
)

final case class ManifestV1(
  name:          String,
  tag:           String,
  fsLayers:      List[FsLayer],
  architecture:  String,
  history:       List[V1Compatibility],
  signatures:    Option[List[Signature]],
  schemaVersion: Int                     = 1
) extends Manifest

final case class Descriptor(
  mediaType: Option[String],
  size:      Long,
  digest:    String
)

final case class ManifestV2(
  schemaVersion: Int              = 2,
  mediaType:     String           = "application/vnd.docker.distribution.manifest.v2+json",
  config:        Descriptor,
  layers:        List[Descriptor]
) extends Manifest
