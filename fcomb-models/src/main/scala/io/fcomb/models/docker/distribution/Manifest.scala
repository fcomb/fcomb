package io.fcomb.models.docker.distribution

import java.time.ZonedDateTime

sealed trait SchemaManifest {
  val schemaVersion: Int
}

final object SchemaV1 {
  case class FsLayer(
    blobSum: String
  )

  case class Protected(
    formatLength: Int,
    formatTail:   String,
    time:         ZonedDateTime
  )

  case class SignatureHeader(
    jwk: Map[String, String],
    alg: String
  )

  case class Signature(
    header:      SignatureHeader,
    signature:   String,
    `protected`: String
  )

  case class ContainerConfig(
    cmd: List[String]
  )

  case class V1Compatibility(
    id:              String,
    parent:          Option[String],
    comment:         Option[String],
    containerConfig: Option[ContainerConfig],
    author:          Option[String],
    throwAway:       Option[Boolean]
  )

  case class Manifest(
    name:          String,
    tag:           String,
    fsLayers:      List[FsLayer],
    architecture:  String,
    history:       List[V1Compatibility],
    signatures:    Option[List[Signature]],
    schemaVersion: Int                     = 1
  ) extends SchemaManifest
}

final object SchemaV2 {
  case class Descriptor(
    mediaType: Option[String],
    size:      Long,
    digest:    String
  )

  case class Manifest(
    schemaVersion: Int              = 2,
    mediaType:     String           = "application/vnd.docker.distribution.manifest.v2+json",
    config:        Descriptor,
    layers:        List[Descriptor]
  ) extends SchemaManifest
}
