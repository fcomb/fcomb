package io.fcomb

import io.fcomb.models.errors.ValidationException
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{existentials, implicitConversions, postfixOps}
import scala.util.matching.Regex
import scalaz._
import scalaz.Scalaz._
import slick.driver.PostgresDriver.api._
import slick.lifted.AppliedCompiledFunction

package object validations {
  type DBIOT[T] = DBIOAction[T, NoStream, Effect.Read]

  type PlainValidationT[T] = Validation[String, T]
  type FutureValidationT[T] = Future[Validation[String, T]]
  type DBIOValidationT[T] = DBIOT[Validation[String, T]]

  type PlainValidation = Validation[String, Unit]
  type FutureValidation = Future[PlainValidation]
  type DBIOValidation = DBIOT[PlainValidation]

  type DBIOActionValidator = DBIOAction[Boolean, NoStream, Effect.Read]

  def eitherT[R](fut: Future[ValidationResult[R]])(
    implicit
    ec: ExecutionContext
  ) =
    EitherT(fut.map(_.disjunction))

  def plainValidation(validations: Seq[PlainValidation]) =
    validations.foldLeft(().success[String]) {
      (acc, v) ⇒ (acc |@| v) { (_, _) ⇒ () }
    }

  def futureValidation(validations: Seq[FutureValidation])(implicit ec: ExecutionContext) =
    Future.sequence(validations).map(plainValidation)

  def dbioValidation(validations: Seq[DBIOValidation])(implicit ec: ExecutionContext) =
    DBIO.sequence(validations).map(plainValidation)

  type ColumnValidation = Validation[ValidationException, Unit]
  type ValidationErrors = List[ValidationException]
  type ValidationResult[T] = Validation[ValidationErrors, T]
  type ValidationResultUnit = ValidationResult[Unit]

  def successResult[E](res: E): validations.ValidationResult[E] =
    res.success[validations.ValidationErrors]

  def validationErrors[M](errors: (String, String)*): ValidationResult[M] =
    errors.map {
      case (param, msg) ⇒ ValidationException(param, msg)
    }.toList.failure[M]

  def validateColumn(column: String, validation: PlainValidation): ColumnValidation =
    validation match {
      case Success(_)   ⇒ ().success
      case Failure(msg) ⇒ ValidationException(column, msg).failure
    }

  def columnValidations2Map(validations: Seq[ColumnValidation]): ValidationResultUnit =
    validations.foldLeft(List.empty[ValidationException]) {
      (acc, v) ⇒
        v match {
          case Success(_) ⇒ acc
          case Failure(e) ⇒ e :: acc
        }
    } match {
      case Nil ⇒ ().success
      case xs  ⇒ xs.failure
    }

  def validatePlain(result: (String, List[PlainValidation])*): ValidationResultUnit =
    result.foldLeft(List.empty[ValidationException]) {
      case (m, (c, v)) ⇒ plainValidation(v) match {
        case Success(_)   ⇒ m
        case Failure(msg) ⇒ ValidationException(c, msg) :: m
      }
    } match {
      case Nil ⇒ ().success
      case xs  ⇒ xs.failure
    }

  def validateDBIO(
    result: (String, List[DBIOValidation])*
  )(
    implicit
    ec: ExecutionContext
  ): DBIOT[ValidationResultUnit] = {
    val emptyList = DBIO
      .successful(List.empty[ValidationException])
      .asInstanceOf[DBIOT[ValidationErrors]]
    result.foldLeft(emptyList) {
      case (m, (c, v)) ⇒ dbioValidation(v).flatMap {
        case Success(_)   ⇒ m
        case Failure(msg) ⇒ m.map(ValidationException(c, msg) :: _)
      }
    }.map {
      case Nil ⇒ ().success
      case xs  ⇒ xs.failure
    }
  }

  object Validations {
    def present(value: String): PlainValidation =
      if (value.isEmpty) "is empty".failure
      else ().success

    val emailRegEx = """\A([a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+)@([a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*)\z""".r

    def email(value: String): PlainValidation =
      if (emailRegEx.findFirstIn(value).isDefined) ().success
      else "invalid email format".failure

    def matches(value: String, regex: Regex, errorMessage: String) =
      if (regex.findFirstIn(value).isDefined) ().success
      else errorMessage.failure

    def unique(
      action: AppliedCompiledFunction[_, Rep[Boolean], Boolean]
    )(implicit ec: ExecutionContext): DBIOValidation = {
      action.result.map {
        case true  ⇒ "not unique".failure
        case false ⇒ ().success
      }
    }

    val uuidRegEx = """(?i)\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\z""".r

    def uuid(value: String): PlainValidation =
      if (uuidRegEx.findFirstIn(value).isDefined) ().success
      else "invalid UUID format".failure

    def notUuid(value: String): PlainValidation =
      if (uuidRegEx.findFirstIn(value).isDefined)
        "cannot be an UUID format".failure
      else ().success

    def lengthRange(value: String, from: Int, to: Int): PlainValidation = {
      if (value.length >= from && value.length <= to) ().success
      else s"length is less than $from or greater than $to".failure
    }

    def maxLength(value: String, to: Int): PlainValidation = {
      if (value.length <= to) ().success
      else s"length is greater than $to".failure
    }

    def minLength(value: String, from: Int): PlainValidation = {
      if (value.length >= from) ().success
      else s"length is less than $from".failure
    }
  }

  implicit class ValidationResultMethods[T](val result: ValidationResult[T]) extends AnyVal {
    def `:::`(result2: ValidationResult[T]): ValidationResult[T] =
      (result, result2) match {
        case (Failure(e1), Failure(e2)) ⇒ Failure(e1 ++ e2)
        case (e @ Failure(_), _)        ⇒ e
        case (_, e @ Failure(_))        ⇒ e
        case _                          ⇒ result
      }
  }
}
