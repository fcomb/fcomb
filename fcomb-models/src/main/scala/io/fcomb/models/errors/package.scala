package io.fcomb.models

package object errors {
  object ErrorKind extends Enumeration {
    type ErrorKind = Value
    val Request = Value("request")
    val Validation = Value("validation")
    val Internal = Value("internal")
    val Authorization = Value("authorization")
    val Persistent = Value("persistent")
  }

  case class ErrorMessage(
    message: String,
    kind: ErrorKind.ErrorKind,
    param: Option[String] = None,
    code: Option[Int] = None
  )

  case class FailureResponse(
    errors: Seq[ErrorMessage]
  )

  object FailureResponse {
    def fromExceptions(errors: Seq[DtCemException]): FailureResponse =
      FailureResponse(errors.map(_.toErrorMessage()))

    def fromException(error: DtCemException): FailureResponse =
      fromExceptions(List(error))
  }

  sealed trait DtCemException extends Throwable {
    def toErrorMessage(): ErrorMessage
  }

  case class InternalException(message: String) extends DtCemException {
    def toErrorMessage() = ErrorMessage(message, ErrorKind.Internal)
  }

  case object JsonBodyCantBeEmpty extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "JSON body can't be empty",
      ErrorKind.Request
    )
  }

  case object InvalidAuthorizationToken extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Invalid authorization token",
      ErrorKind.Authorization
    )
  }

  case object ExpectedAuthorizationToken extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Expected 'Authorization' header with token or URI 'auth_token' parameter",
      ErrorKind.Authorization
    )
  }

  case object ExpectedToken extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Expected URI 'token' parameter",
      ErrorKind.Request
    )
  }

  case object RecordNotFoundException extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Record not found",
      ErrorKind.Request
    )
  }

  case object ResourceNotFoundException extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Resource not found",
      ErrorKind.Request
    )
  }

  case class ValidationException(param: String, message: String) extends DtCemException {
    def toErrorMessage() = ErrorMessage(message, ErrorKind.Validation, Some(param))
  }
}
