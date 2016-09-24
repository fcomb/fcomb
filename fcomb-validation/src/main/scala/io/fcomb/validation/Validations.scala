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

package io.fcomb.validation

import cats.data.Validated
import scala.util.matching.Regex
import slick.jdbc.PostgresProfile.api._
import slick.lifted.AppliedCompiledFunction
import scala.concurrent.ExecutionContext

object Validations {
  def present(value: String): PlainValidation =
    if (value.isEmpty) Validated.Invalid("is empty")
    else Validated.Valid(())

  val emailRegEx =
    """\A([a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+)@([a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*)\z""".r

  def email(value: String): PlainValidation =
    if (emailRegEx.findFirstIn(value).isDefined) Validated.Valid(())
    else Validated.Invalid("invalid email format")

  def matches(value: String, regex: Regex, errorMessage: String) =
    if (regex.findFirstIn(value).isDefined) Validated.Valid(())
    else Validated.Invalid(errorMessage)

  def unique(action: AppliedCompiledFunction[_, Rep[Boolean], Boolean])(
      implicit ec: ExecutionContext): DBIOValidation = {
    action.result.map { isUnique =>
      if (isUnique) Validated.Invalid("not unique")
      else Validated.Valid(())
    }
  }

  val uuidRegEx = """(?i)\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z""".r

  def uuid(value: String): PlainValidation =
    if (uuidRegEx.findFirstIn(value).isDefined) Validated.Valid(())
    else Validated.Invalid("invalid UUID format")

  def notUuid(value: String): PlainValidation =
    if (uuidRegEx.findFirstIn(value).isDefined)
      Validated.Invalid("cannot be an UUID format")
    else Validated.Valid(())

  def lengthRange(value: String, from: Int, to: Int): PlainValidation = {
    if (value.length >= from && value.length <= to) Validated.Valid(())
    else Validated.Invalid(s"length is less than $from or greater than $to")
  }

  def maxLength(value: String, to: Int): PlainValidation = {
    if (value.length <= to) Validated.Valid(())
    else Validated.Invalid(s"length is greater than $to")
  }

  def minLength(value: String, from: Int): PlainValidation = {
    if (value.length >= from) Validated.Valid(())
    else Validated.Invalid(s"length is less than $from")
  }
}
