package io.fcomb.models.docker.distribution

import scala.collection.immutable
import java.time.ZonedDateTime

sealed trait SchemaManifest {
  val schemaVersion: Int
}

object SchemaV1 {
  final case class FsLayer(
      blobSum: String
  ) {
    def getDigest = Reference.getDigest(this.blobSum)
  }

  final case class Protected(
    formatLength: Int,
    formatTail:   String,
    time:         ZonedDateTime
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

  sealed trait Compatibility

  final case class ContainerConfig(
    hostname:        String,
    domainname:      String,
    user:            String,
    attachStdin:     Boolean,
    attachStdout:    Boolean,
    attachStderr:    Boolean,
    exposedPorts:    Option[immutable.Map[String, Unit]],
    tty:             Boolean,
    openStdin:       Boolean,
    stdinOnce:       Boolean,
    env:             List[String],
    cmd:             List[String],
    argsEscaped:     Option[Boolean],
    image:           String,
    volumes:         immutable.Map[String, Unit],
    workingDir:      String,
    entrypoint:      List[String],
    networkDisabled: Option[Boolean],
    macAddress:      Option[String],
    onBuild:         List[String],
    labels:          immutable.Map[String, String],
    stopSignal:      Option[String]
  )

  final case class Config(
    id:              Option[String],
    parent:          Option[String],
    comment:         Option[String],
    created:         Option[ZonedDateTime],
    container:       Option[String],
    containerConfig: Option[ContainerConfig],
    dockerVersion:   Option[String],
    author:          Option[String],
    config:          Option[ContainerConfig],
    architecture:    Option[String],
    os:              Option[String],
    size:            Option[Long],
    throwAway:       Option[Boolean]
  ) extends Compatibility

  final case class LayerContainerConfig(
    cmd: List[String]
  )

  final case class Layer(
    id:              String,
    parent:          Option[String],
    comment:         Option[String],
    created:         Option[ZonedDateTime],
    containerConfig: Option[LayerContainerConfig],
    author:          Option[String],
    throwAway:       Option[Boolean]
  ) extends Compatibility

  final case class Manifest(
    name:          String,
    tag:           String,
    fsLayers:      List[FsLayer],
    architecture:  String,
    history:       List[Compatibility],
    signatures:    List[Signature],
    schemaVersion: Int                 = 1
  ) extends SchemaManifest
}

object SchemaV2 {
  final case class Descriptor(
      mediaType: Option[String],
      size:      Long,
      digest:    String
  ) {
    def getDigest = Reference.getDigest(this.digest)
  }

  final case class Manifest(
    schemaVersion: Int              = 2,
    mediaType:     String           = "application/vnd.docker.distribution.manifest.v2+json",
    config:        Descriptor,
    layers:        List[Descriptor]
  ) extends SchemaManifest

  final case class ImageRootFs(
    `type`:    String,
    diffIds:   List[String],
    baseLayer: Option[String]
  )

  final case class ImageHistory(
      created:    ZonedDateTime,
      author:     Option[String],
      createdBy:  Option[String],
      comment:    Option[String],
      emptyLayer: Option[Boolean]
  ) {
    def isEmptyLayer = this.emptyLayer.contains(true)
  }

  final case class ImageConfig(
    rootFs:       ImageRootFs,
    history:      List[ImageHistory],
    architecture: String
  )
}
