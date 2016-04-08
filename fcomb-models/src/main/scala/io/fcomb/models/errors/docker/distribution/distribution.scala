package io.fcomb.models.errors.docker.distribution

import io.fcomb.models.{Enum, EnumItem}
import io.fcomb.models.errors.{Error, ErrorResponse}

sealed trait DistributionErrorDetail

sealed trait DistributionErrorCode extends EnumItem

final object DistributionErrorCode extends Enum[DistributionErrorCode] {
  final case object DigestInvalid extends DistributionErrorCode {
    val value = "DIGEST_INVALID"
  }

  final case object NameInvalid extends DistributionErrorCode {
    val value = "NAME_INVALID"
  }

  def fromString(value: String) = value match {
    case DigestInvalid.value ⇒ DigestInvalid
    case NameInvalid.value   ⇒ NameInvalid
  }
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
