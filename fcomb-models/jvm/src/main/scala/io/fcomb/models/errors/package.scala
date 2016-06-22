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

package io.fcomb.models

import scala.util.control.NoStackTrace

package object errors {
  final case class ErrorMessage(
      message: String,
      param: Option[String] = None,
      code: Option[Int] = None
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
      param: String,
      message: String
  ) extends DtCemException {
    def toErrorMessage() = ErrorMessage(
      message,
      Some(param)
    )
  }
}
