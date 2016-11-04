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

final case class Error(
    message: String,
    code: ErrorCode,
    param: Option[String] = None
)

final case class Errors(errors: Seq[Error])

object Errors {
  val unauthorized = Error("Unauthorized", ErrorCode.Session)
  val registrationIsDisabled =
    Error("User registration is disabled by administrator", ErrorCode.Security)
  val unknown = internal("Unknown error")

  def deserialization(msg: String = "Deserialization error", param: Option[String] = None) =
    Error(msg, ErrorCode.Deserialization, param)

  def internal(msg: String) =
    Error(msg, ErrorCode.Internal)

  def unknownEnumItem[T <: EnumItem](item: String, enum: Enum[T]) =
    Error(s"$item is not a member of Enum (${enum.entryNames})", ErrorCode.Deserialization)

  def validation(msg: String, param: String) =
    Error(msg, ErrorCode.Validation, Some(param))

  def from(error: Error): Errors = Errors(Seq(error))
}
