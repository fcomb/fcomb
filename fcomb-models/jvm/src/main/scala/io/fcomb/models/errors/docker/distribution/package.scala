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

package io.fcomb.models.errors.docker.distribution

import enumeratum.EnumEntry
import io.fcomb.models.common.{Enum, EnumItem}
import io.fcomb.models.errors.{Error, ErrorResponse}

sealed trait DistributionErrorCode extends EnumItem with EnumEntry.Uppercase

object DistributionErrorCode extends Enum[DistributionErrorCode] {
  final case object BlobUnknown         extends DistributionErrorCode
  final case object BlobUploadInvalid   extends DistributionErrorCode
  final case object BlobUploadUnknown   extends DistributionErrorCode
  final case object Denied              extends DistributionErrorCode
  final case object DigestInvalid       extends DistributionErrorCode
  final case object ManifestBlobUnknown extends DistributionErrorCode
  final case object ManifestInvalid     extends DistributionErrorCode
  final case object ManifestUnknown     extends DistributionErrorCode
  final case object ManifestUnverified  extends DistributionErrorCode
  final case object NameInvalid         extends DistributionErrorCode
  final case object NameUnknown         extends DistributionErrorCode
  final case object SizeInvalid         extends DistributionErrorCode
  final case object TagInvalid          extends DistributionErrorCode
  final case object Unauthorized        extends DistributionErrorCode
  final case object Unknown             extends DistributionErrorCode
  final case object Unsupported         extends DistributionErrorCode

  val values = findValues
}
import DistributionErrorCode._

final case class DistributionError(
    code: DistributionErrorCode,
    message: String
) extends Error

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

final case class DistributionErrorResponse(
    errors: Seq[DistributionError]
) extends ErrorResponse

object DistributionErrorResponse {
  def from(error: DistributionError): DistributionErrorResponse =
    DistributionErrorResponse(Seq(error))
}
