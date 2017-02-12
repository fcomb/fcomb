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

package io.fcomb.models.errors.docker.distribution

import enumeratum.EnumEntry
import io.fcomb.models.common.{Enum, EnumItem}

sealed trait DistributionErrorCode extends EnumItem with EnumEntry.Uppercase

object DistributionErrorCode extends Enum[DistributionErrorCode] {
  case object BlobUnknown         extends DistributionErrorCode
  case object BlobUploadInvalid   extends DistributionErrorCode
  case object BlobUploadUnknown   extends DistributionErrorCode
  case object Denied              extends DistributionErrorCode
  case object DigestInvalid       extends DistributionErrorCode
  case object ManifestBlobUnknown extends DistributionErrorCode
  case object ManifestInvalid     extends DistributionErrorCode
  case object ManifestUnknown     extends DistributionErrorCode
  case object ManifestUnverified  extends DistributionErrorCode
  case object NameInvalid         extends DistributionErrorCode
  case object NameUnknown         extends DistributionErrorCode
  case object SizeInvalid         extends DistributionErrorCode
  case object TagInvalid          extends DistributionErrorCode
  case object Unauthorized        extends DistributionErrorCode
  case object Unknown             extends DistributionErrorCode
  case object Unsupported         extends DistributionErrorCode

  val values = findValues
}
import DistributionErrorCode._

final case class DistributionError(
    code: DistributionErrorCode,
    message: String
)

object DistributionError {
  val blobUnknown = DistributionError(BlobUnknown, "blob unknown to registry")
  val blobUploadInvalid =
    DistributionError(BlobUploadInvalid, "blob upload invalid")
  val blobUploadUnknown = DistributionError(BlobUploadUnknown, "")
  val denied            = DistributionError(Denied, "requested access to the resource is denied")
  val digestInvalid =
    DistributionError(DigestInvalid, "provided digest did not match uploaded content")
  val manifestBlobUnknown = DistributionError(ManifestBlobUnknown, "blob unknown to registry")
  def manifestInvalid(msg: String = "manifest invalid") =
    DistributionError(ManifestInvalid, msg)
  val manifestUnknown =
    DistributionError(DistributionErrorCode.ManifestUnknown, "manifest unknown")
  val manifestUnverified =
    DistributionError(ManifestUnverified, "manifest failed signature verification")
  val nameInvalid = DistributionError(NameInvalid, "invalid repository name")
  val nameUnknown =
    DistributionError(NameUnknown, "repository name not known to registry")
  val sizeInvalid = DistributionError(SizeInvalid, "provided length did not match content length")
  val tagInvalid  = DistributionError(TagInvalid, "manifest tag did not match URI")
  val unauthorized =
    DistributionError(Unauthorized, "authentication required")
  def unknown(msg: String = "unknown error") =
    DistributionError(Unknown, msg)
  val unsupported = DistributionError(Unsupported, "The operation is unsupported.")
}

final case class DistributionErrors(errors: Seq[DistributionError])

object DistributionErrors {
  def from(error: DistributionError): DistributionErrors =
    DistributionErrors(Seq(error))
}
