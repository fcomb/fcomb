package io.fcomb.persist

import io.fcomb.Db._
import io.fcomb.models
import io.fcomb.macros._
import scalikejdbc._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.{ implicitConversions, postfixOps }
import java.util.UUID
import com.github.t3hnar.bcrypt._
import java.time.LocalDateTime

object User extends PersistModelWithPk[models.User, UUID] {
  override val tableName = "users"
  override val columns = Seq(
    "id", "username", "email", "full_name",
    "password_hash", "created_at", "updated_at"
  )

  implicit val mappable = materializeMappable[models.User]

  def apply(rn: ResultName[models.User])(rs: WrappedResultSet): models.User =
    autoConstruct(rs, rn)

  // import scalaz._, Scalaz._

  type ResultContainerValue = (Boolean, String)

  sealed trait ResultContainer {
    def `&&`(v: => ResultContainer)(implicit ec: ExecutionContext): ResultContainer

    def `||`(v: => ResultContainer)(implicit ec: ExecutionContext): ResultContainer

    def unary_!(implicit ec: ExecutionContext): ResultContainer

    protected def and(fst: ResultContainerValue, snd: ResultContainerValue): ResultContainerValue = {
      val errorMsg =
        if (!fst._1) fst._2
        else if (!snd._1) snd._2
        else ""
      (fst._1 && snd._1, errorMsg)
    }

    protected def or(fst: ResultContainerValue, snd: ResultContainerValue): ResultContainerValue = {
      val errorMsg =
        if (fst._1) ""
        else if (snd._1) ""
        else fst._2
      (fst._1 || snd._1, errorMsg)
    }
  }

  sealed trait FutureTestT

  case class FutureValidationResult(f: Future[ResultContainerValue]) extends ResultContainer with FutureTestT {
    def `&&`(v: => ResultContainer)(implicit ec: ExecutionContext): ResultContainer = {
      val res = v match {
        case ValidationResultV(rv) =>
          f.map(and(_, rv))
        case v: FutureValidationResult =>
          for {
            rv1 <- f
            rv2 <- v.f
          } yield and(rv1, rv2)
      }
      FutureValidationResult(res)
    }

    def `||`(v: => ResultContainer)(implicit ec: ExecutionContext): ResultContainer = {
      val res = v match {
        case ValidationResultV(rv) =>
          f.map(or(_, rv))
        case v: FutureValidationResult =>
          for {
            rv1 <- f
            rv2 <- v.f
          } yield or(rv1, rv2)
      }
      FutureValidationResult(res)
    }

    def unary_!(implicit ec: ExecutionContext): ResultContainer =
      FutureValidationResult(f.map {
        case (false, _) =>
          (true, "")
        case (true, errorMsg) =>
          (false, s"not $errorMsg")
      })

    def isValid(): Future[ResultContainerValue] = f
  }

  case class ValidationResultV(rv: ResultContainerValue) extends ResultContainer {
    def `&&`(v: => ResultContainer)(implicit ec: ExecutionContext): ResultContainer = v match {
      case v: ValidationResultV =>
        ValidationResultV(and(rv, v.rv))
      case v: FutureValidationResult =>
        v && this
    }

    def `||`(v: => ResultContainer)(implicit ec: ExecutionContext): ResultContainer = v match {
      case v: ValidationResultV =>
        ValidationResultV(or(rv, v.rv))
      case v: FutureValidationResult =>
        v || this
    }

    def unary_!(implicit ec: ExecutionContext): ResultContainer = rv match {
      case (false, _) =>
        ValidationResultV(true, "")
      case (true, errorMsg) =>
        ValidationResultV(false, s"not $errorMsg")
    }

    def isValid(): ResultContainerValue = rv
  }

  trait ValidationV[T] {
    def apply(obj: T)(implicit ec: ExecutionContext): ResultContainer

    def `&&`(v: ValidationV[T])(implicit ec: ExecutionContext): ValidationV[T] = {
      def g(obj: T) = apply(obj)

      new ValidationV[T] {
        def apply(obj: T)(implicit ec: ExecutionContext) =
          g(obj) && v(obj)
      }
    }

    def `||`(v: ValidationV[T])(implicit ec: ExecutionContext): ValidationV[T] = {
      def g(obj: T)(implicit ec: ExecutionContext) = apply(obj)

      new ValidationV[T] {
        def apply(obj: T)(implicit ec: ExecutionContext) =
          g(obj) || v(obj)
      }
    }

    def unary_!(implicit ec: ExecutionContext): ValidationV[T] = {
      def g(obj: T)(implicit ec: ExecutionContext) = apply(obj)

      new ValidationV[T] {
        def apply(obj: T)(implicit ec: ExecutionContext) =
          !g(obj)
      }
    }
  }

  trait Validator[T] {
    def apply(obj: T): ResultContainer
  }

  trait PresentValidator[T] extends Validator[T]

  implicit object StringPresentValidator extends PresentValidator[String] {
    def apply(s: String) =
      ValidationResultV(s.nonEmpty, "is empty")
  }

  object ValidationVV {
    def present[T](implicit v: PresentValidator[T]): ValidationV[T] =
      new ValidationV[T] {
        def apply(obj: T)(implicit ec: ExecutionContext) = v(obj)
      }

    def notEmpty[T](implicit v: PresentValidator[T]): ValidationV[T] =
      present[T]

    def futureCheck[T]: ValidationV[T] =
      new ValidationV[T] {
        def apply(obj: T)(implicit ec: ExecutionContext) = FutureValidationResult(
          Future.successful(true, "error")
        )
      }
  }
  import ValidationVV._

  implicit class StringV(val s: String) extends AnyVal {
    def is(v: ValidationV[String])(implicit ec: ExecutionContext): ResultContainer =
      v(s)
  }

  import scala.concurrent.ExecutionContext.Implicits.global
  val res = "wow".is(present && !notEmpty)

  // TODO: split plain and future into to separate classes

  val m = Map(
    "name" -> "wow".is(present && !notEmpty),
    "lol" -> "kek".is(futureCheck)
  )



  import scalaz._, Scalaz._

  def create(
    email:    String,
    username: String,
    password: String,
    fullName: Option[String]
  )(implicit ec: ExecutionContext): Future[models.User] = {
    val timeAt = LocalDateTime.now()
    create(models.User(
      id = UUID.randomUUID(),
      email = email,
      username = username,
      fullName = fullName,
      passwordHash = password.bcrypt(generateSalt),
      createdAt = timeAt,
      updatedAt = timeAt
    ))
  }
}
