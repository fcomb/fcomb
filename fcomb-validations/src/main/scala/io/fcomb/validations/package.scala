package io.fcomb

import slick.driver.PostgresDriver.api._
import slick.lifted.AppliedCompiledFunction
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.{ implicitConversions, postfixOps, existentials }
import scala.annotation.tailrec
import scalaz._, Scalaz._

package object validations {
  type DBIOT[T] = DBIOAction[T, NoStream, Effect.Read]

  type PlainValidationT[T] = Validation[NonEmptyList[String], T]
  type FutureValidationT[T] = Future[Validation[NonEmptyList[String], T]]
  type DBIOValidationT[T] = DBIOT[Validation[NonEmptyList[String], T]]

  type PlainValidation = Validation[NonEmptyList[String], Unit]
  type FutureValidation = Future[PlainValidation]
  type DBIOValidation = DBIOT[PlainValidation]

  type DBIOActionValidator = DBIOAction[Boolean, NoStream, Effect.Read]

  def plainValidation(validations: Seq[PlainValidation]) =
    validations.foldLeft(().successNel[String]) {
      (acc, v) => (acc |@| v) { (_, _) => () }
    }

  def futureValidation(validations: Seq[FutureValidation])(implicit ec: ExecutionContext) =
    Future.sequence(validations).map(plainValidation)

  def dbioValidation(validations: Seq[DBIOValidation])(implicit ec: ExecutionContext) =
    DBIO.sequence(validations).map(plainValidation)

  type ColumnErrors = (String, NonEmptyList[String])
  type ColumnValidation = Validation[ColumnErrors, Unit]
  type ValidationErrors = Map[String, List[String]]
  type ValidationResult[T] = Validation[ValidationErrors, T]
  type ValidationResultUnit = ValidationResult[Unit]

  val emptyValidationErrors: ValidationErrors = Map.empty[String, List[String]]

  def validationErrors[M](errors: (String, String)*): ValidationResult[M] =
    errors.map {
      case (k, v) => (k, List(v))
    }.toMap.failure[M]

  def validateColumn(column: String, validation: PlainValidation): ColumnValidation =
    validation match {
      case Success(_)      => ().success
      case Failure(errors) => (column, errors).failure
    }

  def columnValidations2Map(validations: Seq[ColumnValidation]): ValidationResult[Unit] = {
    val res = validations.foldLeft(List.empty[ColumnErrors]) {
      (acc, v) =>
        v match {
          case Success(_) => acc
          case Failure(e) => e :: acc
        }
    }
    if (res.isEmpty) ().success
    else res.foldLeft(Map.empty[String, List[String]]) {
      case (acc, (c, v)) =>
        acc + ((c, v.toList ::: acc.getOrElse(c, List.empty)))
    }.failure
  }

  def validatePlain(result: (String, List[PlainValidation])*): ValidationResult[Unit] = {
    val res = result.foldLeft(Map.empty[String, List[String]]) {
      case (m, (c, v)) => plainValidation(v) match {
        case Success(_) => m
        case Failure(r) => m + ((c, r.toList))
      }
    }
    if (res.isEmpty) ().success
    else res.failure
  }

  def validateDBIO(result: (String, List[DBIOValidation])*)(implicit ec: ExecutionContext): DBIOT[ValidationResult[Unit]] = {
    val emptyMap = DBIO
      .successful(Map.empty[String, List[String]])
      .asInstanceOf[DBIOT[ValidationErrors]]
    val res = result.foldLeft(emptyMap) {
      case (m, (c, v)) => dbioValidation(v).flatMap {
        case Success(_) => m
        case Failure(r) => m.map(_ + ((c, r.toList)))
      }
    }
    res.map {
      case m if m.isEmpty => ().success
      case m => m.failure
    }
  }

  object Validations {
    def present(value: String): PlainValidation =
      if (value.isEmpty) "is empty".failureNel
      else ().successNel

    val emailRegEx = """\A([a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+)@([a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*)\z""".r

    def email(value: String): PlainValidation =
      if (emailRegEx.findFirstIn(value).isDefined) ().successNel
      else "invalid email format".failureNel

    def unique(action: AppliedCompiledFunction[_, Rep[Boolean], Boolean])(implicit ec: ExecutionContext): DBIOValidation = {
      action.result.map {
        case true => "not unique".failureNel
        case false => ().successNel
      }
    }

    def lengthRange(value: String, from: Int, to: Int): PlainValidation = {
      if (value.length >= from && value.length <= to) ().successNel
      else "".failureNel
    }
  }

  implicit class ValidationResultMethods[T](val result: ValidationResult[T]) extends AnyVal {
    def `:::`(result2: ValidationResult[T]): ValidationResult[T] = {
      (result, result2) match {
        case (Failure(e1), Failure(e2)) => Failure(e1 ++ e2)
        case (e @ Failure(_), _) => e
        case (_, e @ Failure(_)) => e
        case _ => result
      }
    }
  }
}
