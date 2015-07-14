package io.fcomb

import scala.concurrent.{ ExecutionContext, Future }
import scala.language.{ implicitConversions, postfixOps, existentials }
import scalaz._, Scalaz._

package object validations {
  type ValidationResult = (Boolean, String)

  sealed trait ValidationResultChain {
    def `&&`(v: => ValidationResultChain)(implicit ec: ExecutionContext): ValidationResultChain

    def `||`(v: => ValidationResultChain)(implicit ec: ExecutionContext): ValidationResultChain

    def unary_!(implicit ec: ExecutionContext): ValidationResultChain

    protected def and(fst: ValidationResult, snd: ValidationResult): ValidationResult = {
      val errorMsg =
        if (!fst._1) fst._2
        else if (!snd._1) snd._2
        else ""
      (fst._1 && snd._1, errorMsg)
    }

    protected def or(fst: ValidationResult, snd: ValidationResult): ValidationResult = {
      val errorMsg =
        if (fst._1) ""
        else if (snd._1) ""
        else fst._2
      (fst._1 || snd._1, errorMsg)
    }
  }

  case class FutureValidationResult(f: Future[ValidationResult]) extends ValidationResultChain {
    def `&&`(v: => ValidationResultChain)(implicit ec: ExecutionContext): ValidationResultChain = {
      val res = v match {
        case PlainValidationResult(rv) =>
          f.map(and(_, rv))
        case v: FutureValidationResult =>
          for {
            rv1 <- f
            rv2 <- v.f
          } yield and(rv1, rv2)
      }
      FutureValidationResult(res)
    }

    def `||`(v: => ValidationResultChain)(implicit ec: ExecutionContext): ValidationResultChain = {
      val res = v match {
        case PlainValidationResult(rv) =>
          f.map(or(_, rv))
        case v: FutureValidationResult =>
          for {
            rv1 <- f
            rv2 <- v.f
          } yield or(rv1, rv2)
      }
      FutureValidationResult(res)
    }

    def unary_!(implicit ec: ExecutionContext): ValidationResultChain =
      FutureValidationResult(f.map {
        case (false, _) =>
          (true, "")
        case (true, errorMsg) =>
          (false, s"not $errorMsg")
      })
  }

  case class PlainValidationResult(rv: ValidationResult) extends ValidationResultChain {
    def `&&`(v: => ValidationResultChain)(implicit ec: ExecutionContext): ValidationResultChain = v match {
      case v: PlainValidationResult =>
        PlainValidationResult(and(rv, v.rv))
      case v: FutureValidationResult =>
        v && this
    }

    def `||`(v: => ValidationResultChain)(implicit ec: ExecutionContext): ValidationResultChain = v match {
      case v: PlainValidationResult =>
        PlainValidationResult(or(rv, v.rv))
      case v: FutureValidationResult =>
        v || this
    }

    def unary_!(implicit ec: ExecutionContext): ValidationResultChain = rv match {
      case (false, _) =>
        PlainValidationResult(true, "")
      case (true, errorMsg) =>
        PlainValidationResult(false, s"not $errorMsg")
    }
  }

  trait Effect
  object Effect {
    trait Plain extends Effect
    trait Future extends Effect
    trait IO extends Effect
    trait DBIOAction extends Effect
    trait All extends Plain with Future with IO with DBIOAction
  }

  trait ValidationWithEffect[T, E <: Effect] {
    def apply(obj: T)(implicit ec: ExecutionContext): ValidationResultChain

    def `&&`[E2 <: Effect](v: ValidationWithEffect[T, E2])(implicit ec: ExecutionContext) = {
      def g(obj: T) = apply(obj)

      new ValidationWithEffect[T, E with E2] {
        def apply(obj: T)(implicit ec: ExecutionContext) =
          g(obj) && v(obj)
      }
    }

    def `||`[E2 <: Effect](v: ValidationWithEffect[T, E2])(implicit ec: ExecutionContext) = {
      def g(obj: T)(implicit ec: ExecutionContext) = apply(obj)

      new ValidationWithEffect[T, E with E2] {
        def apply(obj: T)(implicit ec: ExecutionContext) =
          g(obj) || v(obj)
      }
    }

    def unary_!(implicit ec: ExecutionContext) = {
      def g(obj: T)(implicit ec: ExecutionContext) = apply(obj)

      new ValidationWithEffect[T, E] {
        def apply(obj: T)(implicit ec: ExecutionContext) =
          !g(obj)
      }
    }
  }

  trait Validator[T] {
    def apply(obj: T): ValidationResultChain
  }

  trait PresentValidator[T] extends Validator[T]

  trait UniqueValidator[T] extends Validator[T]

  implicit object StringPresentValidator extends PresentValidator[String] {
    def apply(s: String) =
      PlainValidationResult(s.nonEmpty, "is empty")
  }

  val emailRegEx = """\A([a-zA-Z0-9.!#$%&’*+/=?^_`{|}~-]+)@([a-zA-Z0-9-]+(?:\.[a-zA-Z0-9-]+)*)\z""".r

  trait ValidationMethods {
    def present[T](implicit v: PresentValidator[T]) =
      new ValidationWithEffect[T, Effect.Plain] {
        def apply(obj: T)(implicit ec: ExecutionContext) = v(obj)
      }

    def isEmail =
      new ValidationWithEffect[String, Effect.Plain] {
        def apply(s: String)(implicit ec: ExecutionContext) =
          PlainValidationResult(emailRegEx.findFirstIn(s).isDefined, "invalid email format")
      }
  }
  object Validations extends ValidationMethods

  sealed trait ValidationContainerChain[+E <: Effect] {
    val containers: List[ColumnValidationContainer[_]]

    def `::`[E2 <: Effect](chain: ValidationContainerChain[E2]): ValidationContainerChain[E with E2] = {
      val chainContainers = chain.containers ::: this.containers
      new ValidationsContainer[E with E2] {
        val containers = chainContainers
      }
    }
  }

  sealed trait ValidationsContainer[E <: Effect] extends ValidationContainerChain[E]

  case class ColumnValidationContainer[E <: Effect](column: String, result: ValidationResultChain) extends ValidationContainerChain[E] {
    val containers = List(this)
  }

  implicit class StringValidators(val value: String) extends AnyVal {
    def is[E <: Effect](column: String, validation: ValidationWithEffect[String, E])(implicit ec: ExecutionContext) =
      ColumnValidationContainer[E](column, validation(value))
  }

  def validateColumn[T, E <: Effect](
    column: String,
    value:  T
  )(
    validation: ValidationWithEffect[T, E]
  )(
    implicit
    ec: ExecutionContext
  ) =
    ColumnValidationContainer[E](column, validation(value))

  def validatePlainChain[E <: Effect](chain: ValidationContainerChain[E])(
    implicit
    ec: ExecutionContext,
    eq: ValidationContainerChain[E] =:= ValidationContainerChain[Effect.Plain]
  ): Map[String, List[String]] \/ Unit = {
    chain.containers.foldLeft(Map.empty[String, List[String]]) { (m, c) =>
      val container = c.asInstanceOf[ColumnValidationContainer[_]]
      val result = container.result.asInstanceOf[PlainValidationResult]
      if (result.rv._1) m
      else m + ((container.column, List(result.rv._2)))
    } match {
      case m if m.isEmpty => ().right
      case m              => m.left
    }
  }

  type ValidationMapResult = Map[String, NonEmptyList[String]]
  type ValidationType[M] = Validation[validations.ValidationMapResult, M]
  type FutureValidationMapResult[T] = Future[ValidationType[T]]

  val emptyValidationMapResult = Map.empty[String, NonEmptyList[String]]

  def validateChainAs[T](successValue: T, chain: ValidationContainerChain[_])(
    implicit
    ec: ExecutionContext
  ): FutureValidationMapResult[T] = {
    val acc = Future.successful(emptyValidationMapResult)
    chain.containers.foldLeft(acc) { (f, c) =>
      f.flatMap { m =>
        val container = c.asInstanceOf[ColumnValidationContainer[_]]
        container.result match {
          case PlainValidationResult(rv) => Future.successful {
            if (rv._1) m
            else m + ((container.column, NonEmptyList(rv._2)))
          }
          case FutureValidationResult(f) => f.map { rv =>
            if (rv._1) m
            else m + ((container.column, NonEmptyList(rv._2)))
          }
        }
      }
    }.map {
      case m if m.isEmpty => successValue.success[ValidationMapResult]
      case m              => m.failure[T]
    }
  }

  def validationErrorsMap[M](errors: (String, String)*): ValidationType[M] =
    errors.map {
      case (k, v) => (k, NonEmptyList(v))
    }.toMap.failure[M]
}
