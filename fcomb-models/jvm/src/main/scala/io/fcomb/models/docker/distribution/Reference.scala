/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

package io.fcomb.models.docker.distribution

sealed trait Reference {
  def value: String

  override def toString = value
}

object Reference {
  final case class Tag(name: String) extends Reference {
    def value = name
  }

  final case class Digest(digest: String) extends Reference {
    def value = s"${ImageManifest.sha256Prefix}$digest"
  }

  def apply(s: String): Reference =
    if (isDigest(s)) Digest(getDigest(s))
    else Tag(s)

  def isDigest(s: String): Boolean =
    s.startsWith(ImageManifest.sha256Prefix)

  def getDigest(digest: String): String =
    digest.drop(ImageManifest.sha256Prefix.length)
}
