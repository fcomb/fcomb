package io.fcomb.models.errors.docker.distribution

import io.fcomb.models.errors.{Error, ErrorResponse}

sealed trait DistributionErrorDetail

object DistributionErrorCode extends Enumeration {
  type DistributionErrorCode = Value
}

sealed trait DistributionError extends Error {
  val code: Option[DistributionErrorCode.DistributionErrorCode]
  val message: Option[String]
  val detail: Option[DistributionErrorDetail]
}

case class DistributionErrorResponse(
  errors: Seq[DistributionError]
) extends ErrorResponse
