package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.model.MediaType

object MediaTypes {
  val `application/vnd.docker.distribution.manifest.v1+json` =
    MediaType.applicationBinary("vnd.docker.distribution.manifest.v1+json", MediaType.Compressible)
  val `application/vnd.docker.distribution.manifest.v1+prettyjws` =
    MediaType.applicationBinary("vnd.docker.distribution.manifest.v1+prettyjws", MediaType.Compressible)
  val `application/vnd.docker.container.image.rootfs.diff+x-gtar` =
    MediaType.applicationBinary("vnd.docker.container.image.rootfs.diff+x-gtar", MediaType.Compressible)
  val `application/vnd.docker.distribution.manifest.v2+json` =
    MediaType.applicationBinary("vnd.docker.distribution.manifest.v2+json", MediaType.Compressible)
  val `application/vnd.docker.container.image.v1+json` =
    MediaType.applicationBinary("vnd.docker.container.image.v1+json", MediaType.Compressible)
  val `application/vnd.docker.image.rootfs.diff.tar.gzip` =
    MediaType.applicationBinary("vnd.docker.image.rootfs.diff.tar.gzip", MediaType.Compressible)
}
