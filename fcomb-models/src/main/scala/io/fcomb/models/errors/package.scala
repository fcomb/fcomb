package io.fcomb.models

package object errors {
  object ErrorStatus extends Enumeration {
    type ErrorStatus = Value
    val Validation = Value("validation")
    val Internal = Value("internal")
    val Authorization = Value("authorization")
    val Persistent = Value("persistent")
  }

  sealed trait ErrorResponse {
    val kind: ErrorStatus.ErrorStatus
  }

  case class MultipleFailureResponse(
    kind: ErrorStatus.ErrorStatus,
    errors: Map[String, List[String]]
  ) extends ErrorResponse

  case class SingleFailureResponse(
    kind: ErrorStatus.ErrorStatus,
    error: String
  ) extends ErrorResponse

  case class ValidationErrorsMap(
    errors: Map[String, List[String]]
  )

  def notFound(status: ErrorStatus.ErrorStatus, name: String) =
    SingleFailureResponse(status, s"$name not found")

  val recordNotFound = notFound(ErrorStatus.Persistent, "record")

  val resourceNotFound = notFound(ErrorStatus.Internal, "resource")

  def unauthorizedError(message: String) =
    SingleFailureResponse(ErrorStatus.Authorization, message)
}
