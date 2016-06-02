package io.fcomb.models.errors.docker.distribution

import enumeratum.EnumEntry
import io.fcomb.models.{Enum, EnumItem}
import io.fcomb.models.errors.{Error, ErrorResponse}

sealed trait DistributionErrorDetail

sealed trait DistributionErrorCode extends EnumItem with EnumEntry.Uppercase

object DistributionErrorCode extends Enum[DistributionErrorCode] {
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
  final case object Unknown extends DistributionErrorCode

  val values = findValues
}

sealed trait DistributionError extends Error {
  val code: DistributionErrorCode
  val message: String
  val detail: Option[DistributionErrorDetail]
}

object DistributionError {
  final case class DigestInvalid(
      message: String = "provided digest did not match uploaded content"
  ) extends DistributionError {
    val code = DistributionErrorCode.DigestInvalid
    val detail = None
  }

  final case class Unknown(message: String = "unknown error") extends DistributionError {
    val code = DistributionErrorCode.Unknown
    val detail = None
  }

  final case class NameInvalid(message: String = "invalid repository name") extends DistributionError {
    val code = DistributionErrorCode.NameInvalid
    val detail = None
  }

  final case class ManifestUnverified(
      message: String = "manifest failed signature verification"
  ) extends DistributionError {
    val code = DistributionErrorCode.ManifestUnverified
    val detail = None
  }

  final case class ManifestInvalid(message: String = "manifest invalid") extends DistributionError {
    val code = DistributionErrorCode.ManifestInvalid
    val detail = None
  }

  final case class ManifestUnknown(message: String = "manifest unknown") extends DistributionError {
    val code = DistributionErrorCode.ManifestUnknown
    val detail = None
  }

  final case class BlobUploadInvalid(message: String = "blob upload invalid") extends DistributionError {
    val code = DistributionErrorCode.BlobUploadInvalid
    val detail = None
  }

  final case class NameUnknown(message: String = "repository name not known to registry") extends DistributionError {
    val code = DistributionErrorCode.NameUnknown
    val detail = None
  }

  final case class Unauthorized(message: String = "authentication required") extends DistributionError {
    val code = DistributionErrorCode.Unauthorized
    val detail = None
  }
}

final case class DistributionErrorResponse(
  errors: Seq[DistributionError]
) extends ErrorResponse

object DistributionErrorResponse {
  def from(error: DistributionError): DistributionErrorResponse =
    DistributionErrorResponse(Seq(error))
}
