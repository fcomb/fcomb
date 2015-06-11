package io.fcomb.persist

import io.fcomb.models
import io.fcomb.Db._
import io.fcomb.DBIO, DBIO._
import io.fcomb.utils.validation
import scalikejdbc._
import scala.concurrent.{ ExecutionContext, Future }
import scalaz._, syntax.validation._, syntax.applicative._

trait PersistTypes[T] {
  type FieldError = (String, String)
  type ValidationType[M] = Validation[NonEmptyList[FieldError], M]
  type ValidationModel = ValidationType[T]
  type ValidationResult = ValidationType[Unit]
  type ValidationDbResult = Future[DBIOAction[ValidationResult]]

  def recordNotFound(columnName: String, id: String): ValidationModel =
    (columnName, s"not found record with id: $id").failureNel[T]

  def recordNotFoundAsFuture(field: String, id: String): Future[ValidationModel] =
    Future.successful(recordNotFound(field, id))
}

trait PersistModel[T] extends SQLSyntaxSupport[T] with PersistTypes[T] {
  lazy val alias = syntax

  type ModelDBIO = DBIOAction[T]
  type ModelDBIOOption = DBIOAction[Option[T]]

  sealed trait MethodValidation {
    def apply(user: T)(implicit ec: ExecutionContext): Any
  }
  trait PlainMethodValidation extends MethodValidation {
    def apply(user: T)(implicit ec: ExecutionContext): ValidationResult
  }
  trait FutureMethodValidation extends MethodValidation {
    def apply(user: T)(implicit ec: ExecutionContext): Future[ValidationResult]
  }
  trait DbMethodValidation extends MethodValidation {
    def apply(user: T)(implicit ec: ExecutionContext): DBIOAction[ValidationResult]
  }

  type ValidationSeq = (List[PlainMethodValidation], List[FutureMethodValidation], List[DbMethodValidation])

  val emptyValidations: ValidationSeq = (
    List.empty[PlainMethodValidation],
    List.empty[FutureMethodValidation],
    List.empty[DbMethodValidation]
  )

  protected def applyValidations(validations: MethodValidation*): ValidationSeq = {
    validations.foldLeft(emptyValidations) { (acc, item) =>
      item match {
        case v: PlainMethodValidation  => acc.copy(_1 = v :: acc._1)
        case v: FutureMethodValidation => acc.copy(_2 = v :: acc._2)
        case v: DbMethodValidation     => acc.copy(_3 = v :: acc._3)
      }
    }
  }

  val fieldErrorNel = ().successNel[FieldError]

  val emptyDbResult = Future.successful(DBIO.successful(fieldErrorNel))

  val validations: ValidationSeq = emptyValidations

  protected def validate(items: T*)(implicit ec: ExecutionContext): ValidationDbResult = {
    val futureSeq = items.flatMap { i => validations._2.map(_(i)) }
    val futureValidation = Future.sequence(futureSeq).map { res =>
      res.foldLeft(().successNel[FieldError]) { (res, v) =>
        (res |@| v) { (_, _) => () }
      }
    }
    futureValidation.flatMap {
      case Success(_) =>
        val plainSeq = items.flatMap { i => validations._1.map(_(i)) }
        val plainValidation = plainSeq.foldLeft(fieldErrorNel) { (res, v) =>
          (res |@| v) { (_, _) => () }
        }
        plainValidation match {
          case Success(_) =>
            val dbSeq = items.flatMap { i => validations._3.map(_(i)) }
            val dbValidation = DBIO.fold(dbSeq, fieldErrorNel) { (res, v) =>
              (res |@| v) { (_, _) => () }
            }
            Future.successful(dbValidation)
          case Failure(e) => Future.successful(DBIO.successful(e.failure[Unit]))
        }
      case Failure(e) => Future.successful(DBIO.successful(e.failure[Unit]))
    }
  }

  protected def validate(itemOpt: Option[T])(implicit ec: ExecutionContext): ValidationDbResult =
    itemOpt match {
      case Some(item) => validate(item)
      case None       => emptyDbResult
    }

  @inline
  private def recoverPersistExceptions(
    f: Future[ValidationModel]
  )(implicit ec: ExecutionContext): Future[ValidationModel] =
    f.recover {
      case RecordNotFound => ("record", "not found").failureNel[T]
    }

  @inline
  private def runInTransaction[Q](
    q: DBIOAction[Q]
  )(implicit ec: ExecutionContext): Future[Q] =
    run[Q](q.transactionally(TransactionLevel.ReadCommitted)).map(_.toOption.get) // TODO

  protected def validateWithDBIO(items: T*)(f: => DBIOAction[T])(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] = {
    validate(items: _*).flatMap { dbValidation =>
      val dbAction = dbValidation.flatMap {
        case Success(_) => f.map(Success(_))
        case Failure(e) => DBIO.successful(Failure(e))
      }
      recoverPersistExceptions(runInTransaction(dbAction))
    }
  }

  protected def validationsFold(validations: ValidationDbResult*)(implicit ec: ExecutionContext) = {
    Future.sequence(validations).flatMap { validationsResult =>
      val dbValidation = DBIO.fold(validationsResult, fieldErrorNel) { (res, v) =>
        (res |@| v) { (_, _) => () }
      }
      Future.successful(dbValidation)
    }
  }

  protected def validateWithDBIO(validations: ValidationDbResult*)(f: => DBIOAction[T])(implicit ec: ExecutionContext): Future[ValidationModel] = {
    validationsFold(validations: _*).flatMap { dbValidation =>
      val dbAction = dbValidation.flatMap {
        case Success(_) => f.map(Success(_))
        case Failure(e) => DBIO.successful(Failure(e))
      }
      recoverPersistExceptions(runInTransaction(dbAction))
    }
  }

  // def strictUpdateDBIO[E, U, C[_]](
  //   q:    Query[E, U, C],
  //   item: U
  // )(implicit ec: ExecutionContext) = q.update(item).flatMap {
  //   case 0 => DBIO.failed(new RecordNotFound)
  //   case _ => DBIO.successful(item)
  // }

  def validateModel(item: T)(implicit ec: ExecutionContext): ValidationDbResult =
    validate(item)

  def validateModel(itemOpt: Option[T])(implicit ec: ExecutionContext): ValidationDbResult =
    itemOpt match {
      case Some(item) => validate(item)
      case None       => emptyDbResult
    }

  def validateModel(items: Seq[T])(implicit ec: ExecutionContext): ValidationDbResult =
    validationsFold(items.map(validateModel(_)): _*)

  def validateModelWithDBIO(item: T)(f: => DBIOAction[T])(implicit ec: ExecutionContext, m: Manifest[T]) =
    validateWithDBIO(validateModel(item))(f)

  // def createDBIO(item: T): ModelDBIO =
  //   table returning table.map(i => i) += item.asInstanceOf[Q#TableElementType]

  // def createDBIO(items: Seq[T]) =
  //   table returning table.map(i => i) ++= items.asInstanceOf[Seq[Q#TableElementType]]

  // def createDBIO(itemOpt: Option[T])(implicit ec: ExecutionContext): ModelDBIOOption =
  //   itemOpt match {
  //     case Some(item) => createDBIO(item).map(Some(_))
  //     case None       => DBIO.successful(Option.empty[T])
  //   }

  // // TODO: move into validation scope
  // // TODO: add default error message

  protected def unique[E](columnName: String, errorMsg: String)(f: T => SQL[E, _]) =
    new DbMethodValidation {
      def apply(item: T)(implicit ec: ExecutionContext) = {
        // f(item).take(1).exists.result.map {
        //   case true  => (columnName, errorMsg).failureNel[Unit]
        //   case false => ().successNel[FieldError]
        // }
        ???
      }
    }

  protected def email(columnName: String, errorMsg: String)(f: T => String) =
    new PlainMethodValidation {
      def apply(item: T)(implicit ec: ExecutionContext) = {
        if (validation.emailRegEx.findFirstIn(f(item)).isDefined) ().successNel[FieldError]
        else (columnName, errorMsg).failureNel[Unit]
      }
    }

  protected def present(columnName: String, errorMsg: String)(f: T => String) =
    new PlainMethodValidation {
      def apply(item: T)(implicit ec: ExecutionContext) = {
        if (f(item).nonEmpty) ().successNel[FieldError]
        else (columnName, errorMsg).failureNel[Unit]
      }
    }

  protected def maxLength(columnName: String, length: Int, errorMsg: String)(f: T => String) =
    new PlainMethodValidation {
      def apply(item: T)(implicit ec: ExecutionContext) = {
        if (f(item).length <= length) ().successNel[FieldError]
        else (columnName, errorMsg).failureNel[Unit]
      }
    }

  protected def minLength(columnName: String, length: Int, errorMsg: String)(f: T => String) =
    new PlainMethodValidation {
      def apply(item: T)(implicit ec: ExecutionContext) = {
        if (f(item).length >= length) ().successNel[FieldError]
        else (columnName, errorMsg).failureNel[Unit]
      }
    }

  protected def rangeLength(columnName: String, range: (Int, Int), errorMsg: String)(f: T => String) =
    new PlainMethodValidation {
      def apply(item: T)(implicit ec: ExecutionContext) = {
        val length = f(item).length
        if (length >= range._1 && length <= range._2) ().successNel[FieldError]
        else (columnName, errorMsg).failureNel[Unit]
      }
    }
}


