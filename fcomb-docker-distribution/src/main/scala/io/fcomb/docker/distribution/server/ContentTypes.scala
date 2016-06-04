package io.fcomb.docker.distribution.server

import akka.http.scaladsl.model.{ContentType, HttpCharsets}

object ContentTypes {
  val `application/vnd.docker.distribution.manifest.v1+json` =
    ContentType(MediaTypes.`application/vnd.docker.distribution.manifest.v1+json`, HttpCharsets.`UTF-8`)
  val `application/vnd.docker.distribution.manifest.v1+prettyjws` =
    ContentType(MediaTypes.`application/vnd.docker.distribution.manifest.v1+prettyjws`, HttpCharsets.`UTF-8`)
  val `application/vnd.docker.container.image.rootfs.diff+x-gtar` =
    ContentType(MediaTypes.`application/vnd.docker.container.image.rootfs.diff+x-gtar`)
  val `application/vnd.docker.distribution.manifest.v2+json` =
    ContentType(MediaTypes.`application/vnd.docker.distribution.manifest.v2+json`, HttpCharsets.`UTF-8`)
  val `application/vnd.docker.container.image.v1+json` =
    ContentType(MediaTypes.`application/vnd.docker.container.image.v1+json`)
  val `application/vnd.docker.image.rootfs.diff.tar.gzip` =
    ContentType(MediaTypes.`application/vnd.docker.image.rootfs.diff.tar.gzip`)
}
