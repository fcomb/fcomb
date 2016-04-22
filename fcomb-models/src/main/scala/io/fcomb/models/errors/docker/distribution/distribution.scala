package io.fcomb.models.errors.docker.distribution

import enumeratum.EnumEntry
import io.fcomb.models.{Enum, EnumItem}
import io.fcomb.models.errors.{Error, ErrorResponse}

sealed trait DistributionErrorDetail

sealed trait DistributionErrorCode extends EnumItem with EnumEntry.Uppercase

final object DistributionErrorCode extends Enum[DistributionErrorCode] {
  final case object BlobUnknown extends DistributionErrorCode
  final case object BlobUploadInvalid extends DistributionErrorCode
  final case object BlobUploadUnknown extends DistributionErrorCode
  final case object ManifestBlobUnknown extends DistributionErrorCode
  final case object ManifestInvalid extends DistributionErrorCode
  final case object ManifestUnknown extends DistributionErrorCode
  final case object ManifestUnverified extends DistributionErrorCode
  final case object NameUnknown extends DistributionErrorCode
  final case object SizeInvalid extends DistributionErrorCode
  final case object TagInvalid extends DistributionErrorCode
  final case object Unauthorized extends DistributionErrorCode
  final case object Denied extends DistributionErrorCode
  final case object Unsupported extends DistributionErrorCode
  final case object DigestInvalid extends DistributionErrorCode
  final case object NameInvalid extends DistributionErrorCode

  val values = findValues
}

sealed trait DistributionError extends Error {
  val code: DistributionErrorCode
  val message: String
  val detail: Option[DistributionErrorDetail]
}

final object DistributionError {
  final case class DigestInvalid(message: String = "provided digest did not match uploaded content") extends DistributionError {
    val code = DistributionErrorCode.DigestInvalid
    val detail = None
  }

  final case class NameInvalid(message: String = "invalid repository name") extends DistributionError {
    val code = DistributionErrorCode.NameInvalid
    val detail = None
  }
}

final case class DistributionErrorResponse(
  errors: Seq[DistributionError]
) extends ErrorResponse

final object DistributionErrorResponse {
  def from(error: DistributionError): DistributionErrorResponse =
    DistributionErrorResponse(Seq(error))
}
