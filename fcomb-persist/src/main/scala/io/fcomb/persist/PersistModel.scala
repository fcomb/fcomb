package io.fcomb.persist

import io.fcomb.models
import io.fcomb.Db._
import slick.jdbc.TransactionIsolation
import scala.concurrent.{ ExecutionContext, Future, blocking }
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.RichPostgresDriver.IntoInsertActionComposer
import scalaz._, syntax.validation._, syntax.applicative._
import java.util.UUID

trait PersistTypes[T] {
  type FieldError = (String, String)
  type ValidationType[M] = Validation[NonEmptyList[FieldError], M]
  type ValidationModel = ValidationType[T]
  type ValidationResult = ValidationType[Unit]
  type ValidationDbResult = Future[DBIOAction[ValidationResult, NoStream, Effect.All]]

  def recordNotFound(columnName: String, id: String): ValidationModel =
    (columnName, s"not found record with id: $id").failureNel[T]

  def recordNotFoundAsFuture(field: String, id: String): Future[ValidationModel] =
    Future.successful(recordNotFound(field, id))
}

trait PersistModel[T, Q <: Table[T]] extends PersistTypes[T] {
  val table: TableQuery[Q]

  type ModelDBIO = DBIOAction[T, NoStream, Effect.All]
  type ModelDBIOOption = DBIOAction[Option[T], NoStream, Effect.All]

  sealed trait DBException extends Exception
  class RecordNotFound extends DBException

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
    def apply(user: T)(implicit ec: ExecutionContext): DBIOAction[ValidationResult, NoStream, Effect.All]
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

  val validations = emptyValidations

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
      case _: RecordNotFound => ("record", "not found").failureNel[T]
    }

  @inline
  private def runInTransaction[Q](
    q: DBIOAction[Q, NoStream, Effect.All]
  )(implicit ec: ExecutionContext): Future[Q] =
    db.run(q.transactionally.withTransactionIsolation(TransactionIsolation.ReadCommitted))

  protected def validateWith(items: T*)(f: => DBIOAction[T, NoStream, Effect.All])(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] = {
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

  protected def validateWith(validations: ValidationDbResult*)(f: => DBIOAction[T, NoStream, Effect.All])(implicit ec: ExecutionContext): Future[ValidationModel] = {
    validationsFold(validations: _*).flatMap { dbValidation =>
      val dbAction = dbValidation.flatMap {
        case Success(_) => f.map(Success(_))
        case Failure(e) => DBIO.successful(Failure(e))
      }
      recoverPersistExceptions(runInTransaction(dbAction))
    }
  }

  protected def validateWithAsChain(
    validations: ValidationDbResult*
  )(
    beforeF: => DBIOAction[_, NoStream, Effect.All],
    afterF:  => DBIOAction[T, NoStream, Effect.All]
  )(implicit ec: ExecutionContext, m: Manifest[T]) = {
    validationsFold(validations: _*).flatMap { dbValidation =>
      val dbAction = dbValidation.flatMap {
        case Success(_) => afterF.map(Success(_))
        case Failure(e) => DBIO.successful(Failure(e))
      }
      recoverPersistExceptions(runInTransaction(beforeF.andThen(dbAction)))
    }
  }

  def strictUpdateDBIO[E, U, C[_]](
    q:    Query[E, U, C],
    item: U
  )(implicit ec: ExecutionContext) = q.update(item).flatMap {
    case 0 => DBIO.failed(new RecordNotFound)
    case _ => DBIO.successful(item)
  }

  def validateModel(item: T)(implicit ec: ExecutionContext): ValidationDbResult =
    validate(item)

  def validateModel(itemOpt: Option[T])(implicit ec: ExecutionContext): ValidationDbResult =
    itemOpt match {
      case Some(item) => validate(item)
      case None       => emptyDbResult
    }

  def validateModel(items: Seq[T])(implicit ec: ExecutionContext): ValidationDbResult =
    validationsFold(items.map(validateModel(_)): _*)

  def validateModelWith(item: T)(f: => DBIOAction[T, NoStream, Effect.All])(implicit ec: ExecutionContext, m: Manifest[T]) =
    validateWith(validateModel(item))(f)

  def validateModelWithAsChain(item: T)(
    beforeF: => DBIOAction[_, NoStream, Effect.All],
    afterF:  => DBIOAction[T, NoStream, Effect.All]
  )(
    implicit
    ec: ExecutionContext, m: Manifest[T]
  ) =
    validateWithAsChain(validateModel(item))(beforeF, afterF)

  def createDBIO(item: T): ModelDBIO =
    table returning table.map(i => i) += item.asInstanceOf[Q#TableElementType]

  def createDBIO(items: Seq[T]) =
    table returning table.map(i => i) ++= items.asInstanceOf[Seq[Q#TableElementType]]

  def createDBIO(itemOpt: Option[T])(implicit ec: ExecutionContext): ModelDBIOOption =
    itemOpt match {
      case Some(item) => createDBIO(item).map(Some(_))
      case None       => DBIO.successful(Option.empty[T])
    }
}

trait PersistTableWithPk[T] { this: Table[_] =>
  def id: Rep[T]
}

trait PersistTableWithUuidPk extends PersistTableWithPk[UUID] { this: Table[_] =>
  def id = column[UUID]("id", O.PrimaryKey)
}

trait PersistTableWithAutoIntPk extends PersistTableWithPk[Int] { this: Table[_] =>
  def id = column[Int]("id", O.AutoInc, O.PrimaryKey)
}

trait PersistModelWithPk[Id, T <: models.ModelWithPk[_, Id], Q <: Table[T] with PersistTableWithPk[Id]] extends PersistModel[T, Q] {
  val tableWithId: IntoInsertActionComposer[T, T]

  @inline
  def findByIdQuery(id: T#IdType): Query[Q, T, Seq]

  override def createDBIO(item: T) =
    tableWithId += item

  override def createDBIO(items: Seq[T]) =
    tableWithId ++= items

  @inline
  def mapModel(item: T): T = item

  def create(item: T)(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] = {
    val mappedItem = mapModel(item)
    validateWith(mappedItem) {
      tableWithId += mappedItem
    }
  }

  def findByIdDBIO(id: T#IdType) =
    findByIdQuery(id).take(1).result.headOption

  def findById(id: T#IdType): Future[Option[T]] =
    db.run(findByIdDBIO(id))

  // def update(item: T)(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] = {
  //   val mappedItem = mapModel(item)
  //   validateWith(mappedItem) {
  //     strictUpdateDBIO(table.filter(_.id === mappedItem.id), mappedItem)
  //   }
  // }

  // // TODO: return Unit or errors
  def destroy(id: T#IdType)(implicit ec: ExecutionContext) =
    db
      .run(findByIdQuery(id).delete)
}

trait PersistModelWithUuid[T <: models.ModelWithUuid, Q <: Table[T] with PersistTableWithUuidPk] extends PersistModelWithPk[UUID, T, Q] {
  val tableWithId = table returning table.map(_.id) into ((item, _) => item)

  def findByIdQuery(id: T#IdType): Query[Q, T, Seq] =
    table.filter(_.id === id)
}
