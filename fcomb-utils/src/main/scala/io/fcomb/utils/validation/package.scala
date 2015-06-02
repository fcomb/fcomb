package io.fcomb.utils

import scala.reflect.runtime.universe._
import scalaz._, syntax.validation._, syntax.either._, Validation.FlatMap._
import spray.json._
import scala.util

package object validation {
  type ValidationError[T] = Validation[NonEmptyList[String], T]
  type ValidationResult[T] = Validation[NonEmptyList[(String, String)], T]

  object IntJsonFormat extends JsonFormat[Int] {
    def write(n: Int) = JsNumber(n)

    def read(value: JsValue): Int = value match {
      case JsString(s) if s.forall(Character.isDigit) =>
        s.toInt
      case JsNumber(n) =>
        n.intValue
      case _ =>
        throw new DeserializationException("not an integer")
    }
  }

  object LongJsonFormat extends JsonFormat[Long] {
    def write(n: Long) = JsString(n.toString)

    def read(value: JsValue): Long = value match {
      case JsString(s) if s.forall(Character.isDigit) =>
        s.toLong
      case JsNumber(n) =>
        n.longValue
      case _ =>
        throw new DeserializationException("not a long")
    }
  }

  object DoubleJsonFormat extends JsonFormat[Double] {
    def write(n: Double) = JsNumber(n)

    def read(value: JsValue): Double = value match {
      case JsString(s) if s.forall(c => Character.isDigit(c) || c == '.') =>
        s.toDouble
      case JsNumber(n) =>
        n.doubleValue
      case _ =>
        throw new DeserializationException("not a double")
    }
  }

  // TODO: move into json package
  implicit class JsonParserMethods(val json: JsObject) extends AnyVal {
    private def extractField[T](field: String)(implicit r: JsonReader[T], t: TypeTag[T]): ValidationError[Option[T]] = {
      val res = json.fields.get(field)
      util.Try(t match {
        case tt if tt == typeTag[Int] =>
          res.map(IntJsonFormat.read(_).asInstanceOf[T])
        case tt if tt == typeTag[Long] =>
          res.map(LongJsonFormat.read(_).asInstanceOf[T])
        case tt if tt == typeTag[Double] =>
          res.map(DoubleJsonFormat.read(_).asInstanceOf[T])
        case _ =>
          res.map(_.convertTo[T])
      }) match {
        case util.Success(res) => res.successNel[String]
        case util.Failure(e)   => e.getMessage.failureNel[Option[T]]
      }
    }

    private def extractFieldThenApply[T, U](
      field: String
    )(
      f: Option[T] => ValidationResult[U]
    )(implicit r: JsonReader[T], t: TypeTag[T]): ValidationResult[U] = {
      extractField[T](field) match {
        case Success(res) => f(res)
        case Failure(e)   => failureField(field, e)
      }
    }

    def get[T](
      field: String,
      f:     Option[ValidationError[T] => ValidationError[T]] = None
    )(implicit t: TypeTag[T], r: JsonReader[T]): ValidationResult[T] =
      extractFieldThenApply[T, T](field) { res =>
        val v = res.map(_.successNel[String]).getOrElse(
          failureMsg("can't be empty")
        )
        validate(field, v, f)
      }

    def getOrElse[T](
      field:  String,
      orElse: T,
      f:      Option[ValidationError[T] => ValidationError[T]] = None
    )(implicit t: TypeTag[T], r: JsonReader[T]): ValidationResult[T] =
      extractFieldThenApply[T, T](field) { res =>
        val v = res.map(_.successNel[String]).getOrElse(
          orElse.successNel[String]
        )
        validate(field, v, f)
      }

    def getOpt[T](
      field: String,
      f:     Option[ValidationError[T] => ValidationError[T]] = None
    )(implicit t: TypeTag[T], r: JsonReader[T]): ValidationResult[Option[T]] =
      extractFieldThenApply[T, Option[T]](field) { res =>
        validateOpt(field, res.successNel[String], f)
      }

    private def validate[T](
      field: String,
      value: ValidationError[T],
      fOpt:  Option[ValidationError[T] => ValidationError[T]]
    ): ValidationResult[T] = {
      val res = fOpt match {
        case Some(f) => f(value)
        case None    => value
      }
      mapValidate(field, res)
    }

    private def validateOpt[T](
      field: String,
      value: ValidationError[Option[T]],
      fOpt:  Option[ValidationError[T] => ValidationError[T]]
    ): ValidationResult[Option[T]] =
      {
        val res = fOpt match {
          case Some(f) =>
            value.flatMap {
              case Some(s) => f(s.successNel[String]).map(Some(_))
              case None    => None.successNel[String]
            }
          case None => value
        }
        mapValidate(field, res)
      }

    @inline
    private def failureField[T](field: String, e: NonEmptyList[String]): ValidationResult[T] =
      Failure(e.map((field, _)))

    private def mapValidate[T](
      field: String,
      v:     ValidationError[T]
    ): ValidationResult[T] = v match {
      case Success(res) => Success(res)
      case Failure(e)   => failureField(field, e)
    }

    private def failureMsg[T](message: String) = message.failureNel[T]
  }

  val emailRegEx = """\A([a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+)@([a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*)\z""".r

  private object Helpers {
    def applyValidation[T](
      v: ValidationError[T]
    )(
      f: T => String \/ T
    ): ValidationError[T] = v match {
      case Success(res) => f(res) match {
        case \/-(r) => r.successNel[String]
        case -\/(e) => e.failureNel[T]
      }
      case m => m
    }
  }

  implicit class StringValidation(val validation: ValidationError[String]) extends AnyVal {
    def email = Helpers.applyValidation[String](validation) { field =>
      if (emailRegEx.findFirstIn(field).isDefined) field.right
      else "invalid email format".left
    }

    def present = Helpers.applyValidation[String](validation) { field =>
      if (field.nonEmpty) field.right
      else "can't be empty".left
    }

    def min(length: Int) = Helpers.applyValidation[String](validation) { field =>
      if (field.length >= length) field.right
      else s"can't be less than $length characters".left
    }

    def max(length: Int) = Helpers.applyValidation[String](validation) { field =>
      if (field.length <= length) field.right
      else s"can't be more than $length characters".left
    }

    def range(min: Int, max: Int) = Helpers.applyValidation[String](validation) { field =>
      if (field.length >= min && field.length <= max) field.right
      else s"can't be less than $min and more than $max characters".left
    }
  }
}
