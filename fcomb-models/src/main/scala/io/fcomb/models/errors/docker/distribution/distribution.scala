package io.fcomb.models.errors.docker.distribution

import enumeratum._
import io.fcomb.models.errors.{Error, ErrorResponse}

sealed trait DistributionErrorDetail

sealed trait DistributionErrorCode extends EnumEntry

final object DistributionErrorCode extends CirceEnum[DistributionErrorCode] with Enum[DistributionErrorCode] {
  final case object BlobUnknown extends DistributionErrorCode {
    val value = "BLOB_UNKNOWN"
  }

  final case object BlobUploadInvalid extends DistributionErrorCode {
    val value = "BLOB_UPLOAD_INVALID"
  }

  final case object BlobUploadUnknown extends DistributionErrorCode {
    val value = "BLOB_UPLOAD_UNKNOWN"
  }

  final case object ManifestBlobUnknown extends DistributionErrorCode {
    val value = "MANIFEST_BLOB_UNKNOWN"
  }

  final case object ManifestInvalid extends DistributionErrorCode {
    val value = "MANIFEST_INVALID"
  }

  final case object ManifestUnknown extends DistributionErrorCode {
    val value = "MANIFEST_UNKNOWN"
  }

  final case object ManifestUnverified extends DistributionErrorCode {
    val value = "MANIFEST_UNVERIFIED"
  }

  final case object NameUnknown extends DistributionErrorCode {
    val value = "NAME_UNKNOWN"
  }

  final case object SizeInvalid extends DistributionErrorCode {
    val value = "SIZE_INVALID"
  }

  final case object TagInvalid extends DistributionErrorCode {
    val value = "TAG_INVALID"
  }

  final case object Unauthorized extends DistributionErrorCode {
    val value = "UNAUTHORIZED"
  }

  final case object Denied extends DistributionErrorCode {
    val value = "DENIED"
  }

  final case object Unsupported extends DistributionErrorCode {
    val value = "UNSUPPORTED"
  }

  final case object DigestInvalid extends DistributionErrorCode {
    val value = "DIGEST_INVALID"
  }

  final case object NameInvalid extends DistributionErrorCode {
    val value = "NAME_INVALID"
  }

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
