package io.fcomb.models

import application.ApplicationState
import scala.util.control.NoStackTrace

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
    kind:    ErrorKind.ErrorKind,
    param:   Option[String]      = None,
    code:    Option[Int]         = None
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

  sealed trait DtCemException extends NoStackTrace {
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
      "Expected 'Authorization' header with token or URI 'access_token' parameter",
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

  case object CantExtractClientIpAddress extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Can't extract client IP address",
      ErrorKind.Request
    )
  }

  case class UnexpectedState(state: ApplicationState.ApplicationState) extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Unxpected state",
      ErrorKind.Internal,
      Some(state.toString)
    )
  }

  case object CannotStop extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Cannot stop",
      ErrorKind.Request,
      Some("state")
    )
  }

  case object CannotRestart extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Cannot restart",
      ErrorKind.Request,
      Some("state")
    )
  }

  case object CannotRedeploy extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Cannot redeploy",
      ErrorKind.Request,
      Some("state")
    )
  }

  case object CannotScale extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Cannot scale",
      ErrorKind.Request,
      Some("state")
    )
  }

  case object CannotDoAnythingWhenTerminated extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Cannot do anything in terminated state",
      ErrorKind.Request,
      Some("state")
    )
  }

  case object NoNodesAvailable extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "No nodes available",
      ErrorKind.Internal
    )
  }

  case object NodeIsNotAvailable extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Node is not available",
      ErrorKind.Internal
    )
  }

  case object NodeIsTerminated extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Node is terminated",
      ErrorKind.Internal
    )
  }

  case object DockerApiClientIsNotInitialized extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Docker API client is not initialized",
      ErrorKind.Internal
    )
  }

  case object ContainerNotFoundOrTerminated extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Container not found or terminated",
      ErrorKind.Internal
    )
  }

  case object ContainerDockerIdCantBeEmpty extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      "Container docker id cannot be empty",
      ErrorKind.Internal
    )
  }

  case class ValidationException(
      param:   String,
      message: String
  ) extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      message,
      ErrorKind.Validation,
      Some(param)
    )
  }
}
