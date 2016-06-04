package io.fcomb.models

import scala.util.control.NoStackTrace

package object errors {
  final case class ErrorMessage(
    message: String,
    param:   Option[String] = None,
    code:    Option[Int]    = None
  )

  final case class FailureResponse(
    errors: Seq[ErrorMessage]
  )

  object FailureResponse {
    def fromExceptions(errors: Seq[DtCemException]): FailureResponse =
      FailureResponse(errors.map(_.toErrorMessage()))

    def fromException(error: DtCemException): FailureResponse =
      fromExceptions(List(error))
  }

  sealed trait DtCemException extends NoStackTrace {
    def toErrorMessage(): ErrorMessage
  }

  case class InternalException(message: String) extends DtCemException {
    def toErrorMessage() = ErrorMessage(message)
  }

  case object JsonBodyCantBeEmpty extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "JSON body can't be empty"
    )
  }

  case object InvalidAuthorizationToken extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Invalid authorization token"
    )
  }

  case object ExpectedAuthorizationToken extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Expected 'Authorization' header with token or URI 'access_token' parameter"
    )
  }

  case object ExpectedToken extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Expected URI 'token' parameter"
    )
  }

  case object RecordNotFoundException extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Record not found"
    )
  }

  case object ResourceNotFoundException extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Resource not found"
    )
  }

  final case class ValidationException(
      param:   String,
      message: String
  ) extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      message,
      Some(param)
    )
  }
}
