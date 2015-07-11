package io.fcomb.persist

import io.fcomb.Db._
import io.fcomb.models
import io.fcomb.macros._
import scalikejdbc._
import scala.concurrent.{ ExecutionContext, Future }
import scala.language.{ implicitConversions, postfixOps, existentials }
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

  trait Effect
  object Effect {
    trait Plain extends Effect
    trait Future extends Effect
    trait IO extends Effect
    trait DBIOAction extends Effect
    trait All extends Plain with Future with IO with DBIOAction
  }

  trait ValidationV[T, E <: Effect] {
    def apply(obj: T)(implicit ec: ExecutionContext): ResultContainer

    def `&&`[E2 <: Effect](v: ValidationV[T, E2])(implicit ec: ExecutionContext) = {
      def g(obj: T) = apply(obj)

      new ValidationV[T, E with E2] {
        def apply(obj: T)(implicit ec: ExecutionContext) =
          g(obj) && v(obj)
      }
    }

    def `||`[E2 <: Effect](v: ValidationV[T, E2])(implicit ec: ExecutionContext) = {
      def g(obj: T)(implicit ec: ExecutionContext) = apply(obj)

      new ValidationV[T, E with E2] {
        def apply(obj: T)(implicit ec: ExecutionContext) =
          g(obj) || v(obj)
      }
    }

    def unary_!(implicit ec: ExecutionContext) = {
      def g(obj: T)(implicit ec: ExecutionContext) = apply(obj)

      new ValidationV[T, E] {
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
    def present[T](implicit v: PresentValidator[T]) =
      new ValidationV[T, Effect.Plain] {
        def apply(obj: T)(implicit ec: ExecutionContext) = v(obj)
      }

    def notEmpty[T](implicit v: PresentValidator[T]) =
      present[T]

    def futureCheck[T] =
      new ValidationV[T, Effect.Future] {
        def apply(obj: T)(implicit ec: ExecutionContext) = FutureValidationResult(
          Future.successful(true, "error")
        )
      }
  }
  import ValidationVV._

  implicit class StringV(val s: String) extends AnyVal {
    def is[E <: Effect](v: ValidationV[String, E])(implicit ec: ExecutionContext): ResultContainer =
      v(s)
  }

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

  case class ColumnValidationContainer[E <: Effect](column: String, result: ResultContainer) extends ValidationContainerChain[E] {
    val containers = List(this)
  }

  def validateColumn[T, E <: Effect](
    column: String,
    s:      T
  )(
    v: ValidationV[T, E]
  )(
    implicit
    ec: ExecutionContext
  ) =
    ColumnValidationContainer[E](column, v(s))

  import scala.concurrent.ExecutionContext.Implicits.global
  val res = "wow".is(present && !notEmpty)
  val v = present && notEmpty

  // validateP("kek")(v)
  val vr = validateColumn("name", "kek")(v) ::
    validateColumn("name2", "")(v) ::
    validateColumn("name3", "kek2")(v /* || futureCheck*/ )

  import scalaz._, Scalaz._
  def validatePlainChain[E <: Effect](chain: ValidationContainerChain[E])(
    implicit
    ec: ExecutionContext,
    eq: ValidationContainerChain[E] =:= ValidationContainerChain[Effect.Plain]
  ): Map[String, List[String]] \/ Unit = {
    chain.containers.foldLeft(Map.empty[String, List[String]]) { (m, c) =>
      val container = c.asInstanceOf[ColumnValidationContainer[_]]
      val result = container.result.asInstanceOf[ValidationResultV]
      if (result.rv._1) m
      else m + ((container.column, List(result.rv._2)))
    } match {
      case m if m.isEmpty => ().right
      case m              => m.left
    }
  }

  def validateChain[E <: Effect](chain: ValidationContainerChain[E])(
    implicit
    ec: ExecutionContext
  ): Future[Map[String, List[String]] \/ Unit] = {
    val acc = Future.successful(Map.empty[String, List[String]])
    chain.containers.foldLeft(acc) { (f, c) =>
      f.flatMap { m =>
        val container = c.asInstanceOf[ColumnValidationContainer[_]]
        container.result match {
          case ValidationResultV(rv) => Future.successful {
            if (rv._1) m
            else m + ((container.column, List(rv._2)))
          }
          case FutureValidationResult(f) => f.map { rv =>
            if (rv._1) m
            else m + ((container.column, List(rv._2)))
          }
        }
      }
    }.map {
      case m if m.isEmpty => ().right
      case m              => m.left
    }
  }

  val vpc = validatePlainChain(vr)
  println(s"vpc: $vpc")

  validateChain(vr :: validateColumn("future", "kek2")(futureCheck)).map { vc =>
    println(s"vc: $vc")
  }

  // validateP(v && futureCheck)
  // validateP(futureCheck)

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
