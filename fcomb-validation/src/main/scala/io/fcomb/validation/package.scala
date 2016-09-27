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

import cats.data.{Validated, XorT}
import cats.instances.all._
import cats.syntax.cartesian._
import io.fcomb.models.errors.{Error, Errors}
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._

package object validation {
  type DBIOT[T] = DBIOAction[T, NoStream, Effect.Read]

  type PlainValidationT[T]  = Validated[String, T]
  type FutureValidationT[T] = Future[Validated[String, T]]
  type DBIOValidationT[T]   = DBIOT[Validated[String, T]]

  type PlainValidation  = Validated[String, Unit]
  type FutureValidation = Future[PlainValidation]
  type DBIOValidation   = DBIOT[PlainValidation]

  type DBIOActionValidator = DBIOAction[Boolean, NoStream, Effect.Read]

  def eitherT[R](fut: Future[ValidationResult[R]])(implicit ec: ExecutionContext) =
    XorT(fut.map(_.toXor))

  def plainValidation(validations: Seq[PlainValidation]): PlainValidation =
    validations.foldLeft[PlainValidation](Validated.valid[String, Unit](()))(_ <* _)

  def futureValidation(validations: Seq[FutureValidation])(implicit ec: ExecutionContext) =
    Future.sequence(validations).map(plainValidation)

  def dbioValidation(validations: Seq[DBIOValidation])(implicit ec: ExecutionContext) =
    DBIO.sequence(validations).map(plainValidation)

  type ColumnValidation     = Validated[Error, Unit]
  type ValidationErrors     = List[Error]
  type ValidationResult[T]  = Validated[ValidationErrors, T]
  type ValidationResultUnit = ValidationResult[Unit]

  def successResult[E](res: E): validation.ValidationResult[E] =
    Validated.Valid(res)

  def validationErrors[M](errors: (String, String)*): ValidationResult[M] =
    Validated.Invalid(errors.map { case (column, msg) => Errors.validation(msg, column) }.toList)

  def validateColumn(column: String, validation: PlainValidation): ColumnValidation =
    validation match {
      case Validated.Valid(_)     => Validated.Valid(())
      case Validated.Invalid(msg) => Validated.Invalid(Errors.validation(msg, column))
    }

  def columnValidations2Map(validations: Seq[ColumnValidation]): ValidationResultUnit =
    validations.foldLeft(List.empty[Error]) {
      case (acc, Validated.Valid(_))   => acc
      case (acc, Validated.Invalid(e)) => e :: acc
    } match {
      case Nil => Validated.Valid(())
      case xs  => Validated.Invalid(xs)
    }

  def validatePlain(result: (String, List[PlainValidation])*): ValidationResultUnit =
    result.foldLeft(List.empty[Error]) {
      case (m, (c, v)) =>
        plainValidation(v) match {
          case Validated.Valid(_)     => m
          case Validated.Invalid(msg) => Errors.validation(msg, c) :: m
        }
    } match {
      case Nil => Validated.Valid(())
      case xs  => Validated.Invalid(xs)
    }

  def validateDBIO(result: (String, List[DBIOValidation])*)(
      implicit ec: ExecutionContext): DBIOT[ValidationResultUnit] = {
    val emptyList =
      DBIO.successful(List.empty[Error]).asInstanceOf[DBIOT[ValidationErrors]]
    result
      .foldLeft(emptyList) {
        case (m, (c, v)) =>
          dbioValidation(v).flatMap {
            case Validated.Valid(_)     => m
            case Validated.Invalid(msg) => m.map(Errors.validation(msg, c) :: _)
          }
      }
      .map {
        case Nil => Validated.Valid(())
        case xs  => Validated.Invalid(xs)
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
