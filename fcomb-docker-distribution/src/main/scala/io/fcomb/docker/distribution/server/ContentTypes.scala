/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.docker.distribution.server

import akka.http.scaladsl.model.{ContentType, HttpCharsets}

object ContentTypes {
  val `application/vnd.docker.distribution.manifest.v1+json` = ContentType(
    MediaTypes.`application/vnd.docker.distribution.manifest.v1+json`,
    HttpCharsets.`UTF-8`)
  val `application/vnd.docker.distribution.manifest.v1+prettyjws` = ContentType(
    MediaTypes.`application/vnd.docker.distribution.manifest.v1+prettyjws`,
    HttpCharsets.`UTF-8`)
  val `application/vnd.docker.container.image.rootfs.diff+x-gtar` = ContentType(
    MediaTypes.`application/vnd.docker.container.image.rootfs.diff+x-gtar`)
  val `application/vnd.docker.distribution.manifest.v2+json` = ContentType(
    MediaTypes.`application/vnd.docker.distribution.manifest.v2+json`,
    HttpCharsets.`UTF-8`)
  val `application/vnd.docker.container.image.v1+json` = ContentType(
    MediaTypes.`application/vnd.docker.container.image.v1+json`)
  val `application/vnd.docker.image.rootfs.diff.tar.gzip` = ContentType(
    MediaTypes.`application/vnd.docker.image.rootfs.diff.tar.gzip`)
}
