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

package io.fcomb.models.errors

import io.fcomb.models.common.{Enum, EnumItem}

// TODO: rewrite all

final case class InternalException(message: String) extends DtCemException {
  def toErrorMessage() = ErrorMessage(message)
}

final case object JsonBodyCantBeEmpty extends DtCemException {
  def toErrorMessage() = ErrorMessage(
    "JSON body can't be empty"
  )
}

final case object InvalidAuthorizationToken extends DtCemException {
  def toErrorMessage() = ErrorMessage(
    "Invalid authorization token"
  )
}

final case object ExpectedAuthorizationToken extends DtCemException {
  def toErrorMessage() = ErrorMessage(
    "Expected 'Authorization' header with token or URI 'access_token' parameter"
  )
}

final case object ExpectedToken extends DtCemException {
  def toErrorMessage() = ErrorMessage(
    "Expected URI 'token' parameter"
  )
}

final case object RecordNotFoundException extends DtCemException {
  def toErrorMessage() = ErrorMessage(
    "Record not found"
  )
}

final case object ResourceNotFoundException extends DtCemException {
  def toErrorMessage() = ErrorMessage(
    "Resource not found"
  )
}

final case class UnknownEnumItemException[T <: EnumItem](item: String, enum: Enum[T])
    extends DtCemException {
  def toErrorMessage() = {
    ErrorMessage(s"$item is not a member of Enum (${enum.entryNames})")
  }
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

final case object RegistrationIsDisabled extends DtCemException {
  def toErrorMessage() = ErrorMessage("User registration is disabled by administrator")
}
