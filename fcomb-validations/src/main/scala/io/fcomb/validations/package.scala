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

package io.fcomb

import io.fcomb.models.errors.ValidationException
import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex
import cats.data.{XorT, Validated}
import cats.syntax.cartesian._
import cats.instances.all._
import slick.jdbc.PostgresProfile.api._
import slick.lifted.AppliedCompiledFunction

package object validations {
  type DBIOT[T] = DBIOAction[T, NoStream, Effect.Read]

  type PlainValidationT[T]  = Validated[String, T]
  type FutureValidationT[T] = Future[Validated[String, T]]
  type DBIOValidationT[T]   = DBIOT[Validated[String, T]]

  type PlainValidation  = Validated[String, Unit]
  type FutureValidation = Future[PlainValidation]
  type DBIOValidation   = DBIOT[PlainValidation]

  type DBIOActionValidator = DBIOAction[Boolean, NoStream, Effect.Read]

  def eitherT[R](fut: Future[ValidationResult[R]])(
      implicit ec: ExecutionContext
  ) = XorT(fut.map(_.toXor))

  def plainValidation(validations: Seq[PlainValidation]): PlainValidation =
    validations.foldLeft[PlainValidation](Validated.valid[String, Unit](()))(_ <* _)

  def futureValidation(validations: Seq[FutureValidation])(implicit ec: ExecutionContext) =
    Future.sequence(validations).map(plainValidation)

  def dbioValidation(validations: Seq[DBIOValidation])(implicit ec: ExecutionContext) =
    DBIO.sequence(validations).map(plainValidation)

  type ColumnValidation     = Validated[ValidationException, Unit]
  type ValidationErrors     = List[ValidationException]
  type ValidationResult[T]  = Validated[ValidationErrors, T]
  type ValidationResultUnit = ValidationResult[Unit]

  def successResult[E](res: E): validations.ValidationResult[E] =
    Validated.Valid(res)

  def validationErrors[M](errors: (String, String)*): ValidationResult[M] =
    Validated.Invalid(errors.map {
      case (param, msg) => ValidationException(param, msg)
    }.toList)

  def validateColumn(column: String, validation: PlainValidation): ColumnValidation =
    validation match {
      case Validated.Valid(_)     => Validated.Valid(())
      case Validated.Invalid(msg) => Validated.Invalid(ValidationException(column, msg))
    }

  def columnValidations2Map(validations: Seq[ColumnValidation]): ValidationResultUnit =
    validations.foldLeft(List.empty[ValidationException]) { (acc, v) =>
      v match {
        case Validated.Valid(_)   => acc
        case Validated.Invalid(e) => e :: acc
      }
    } match {
      case Nil => Validated.Valid(())
      case xs  => Validated.Invalid(xs)
    }

  def validatePlain(result: (String, List[PlainValidation])*): ValidationResultUnit =
    result.foldLeft(List.empty[ValidationException]) {
      case (m, (c, v)) =>
        plainValidation(v) match {
          case Validated.Valid(_)     => m
          case Validated.Invalid(msg) => ValidationException(c, msg) :: m
        }
    } match {
      case Nil => Validated.Valid(())
      case xs  => Validated.Invalid(xs)
    }

  def validateDBIO(
      result: (String, List[DBIOValidation])*
  )(
      implicit ec: ExecutionContext
  ): DBIOT[ValidationResultUnit] = {
    val emptyList =
      DBIO.successful(List.empty[ValidationException]).asInstanceOf[DBIOT[ValidationErrors]]
    result
      .foldLeft(emptyList) {
        case (m, (c, v)) =>
          dbioValidation(v).flatMap {
            case Validated.Valid(_)     => m
            case Validated.Invalid(msg) => m.map(ValidationException(c, msg) :: _)
          }
      }
      .map {
        case Nil => Validated.Valid(())
        case xs  => Validated.Invalid(xs)
      }
  }

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

  implicit class ValidationResultMethods[T](val result: ValidationResult[T]) extends AnyVal {
    def `:::`(result2: ValidationResult[T]): ValidationResult[T] =
      (result, result2) match {
        case (Validated.Invalid(e1), Validated.Invalid(e2)) => Validated.Invalid(e1 ++ e2)
        case (e @ Validated.Invalid(_), _)                  => e
        case (_, e @ Validated.Invalid(_))                  => e
        case _                                              => result
      }
  }
}
