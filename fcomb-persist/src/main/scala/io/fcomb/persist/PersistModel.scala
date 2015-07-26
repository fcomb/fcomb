package io.fcomb.persist

import io.fcomb.{ models, validations }
import io.fcomb.validations.{ DBIOT, ValidationResult }
import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.IntoInsertActionComposer
import io.fcomb.RichPostgresDriver.api._
import java.util.UUID
import scala.concurrent.{ ExecutionContext, Future, blocking }
import scalaz._
import scalaz.Scalaz._
import slick.jdbc.TransactionIsolation
import slick.lifted.AppliedCompiledFunction

trait PersistTypes[T] {
  type ValidationModel = validations.ValidationResult[T]
  type ValidationDBIOResult = DBIOT[ValidationResult[Unit]]

  def validationError[E](columnName: String, error: String): validations.ValidationResult[E] =
    validations.validationErrors(columnName -> error)

  def validationErrorAsFuture[E](columnName: String, error: String): Future[validations.ValidationResult[E]] =
    Future.successful(validationError(columnName, error))

  def recordNotFound(columnName: String, id: String): ValidationModel =
    validationError(columnName, s"not found record with id: $id")

  def recordNotFoundAsFuture(field: String, id: String): Future[ValidationModel] =
    Future.successful(recordNotFound(field, id))
}

trait PersistModel[T, Q <: Table[T]] extends PersistTypes[T] {
  val table: TableQuery[Q]

  type ModelDBIO = DBIOAction[T, NoStream, Effect.All]
  type ModelDBIOOption = DBIOAction[Option[T], NoStream, Effect.All]

  val validationsOpt = Option.empty[T => Future[validations.ValidationResult[_]]]

  // @inline
  // private def recoverPersistExceptions(
  //   f: Future[ValidationModel]
  // )(implicit ec: ExecutionContext): Future[ValidationModel] =
  //   f.recover {
  //     case _: RecordNotFound => ("record", "not found").failureNel[T]
  //   }

  @inline
  private def runInTransaction[Q](
    q: DBIOAction[Q, NoStream, Effect.All]
  )(implicit ec: ExecutionContext): Future[Q] =
    db.run(q.transactionally.withTransactionIsolation(TransactionIsolation.ReadCommitted))

  protected def validate(t: (ValidationResult[Unit], ValidationDBIOResult))(implicit ec: ExecutionContext): ValidationDBIOResult =
    for {
      plainRes <- DBIO.successful(t._1)
      dbioRes <- t._2
    } yield (plainRes |@| dbioRes) { (_, _) => () }

  protected def validate(item: T)(implicit ec: ExecutionContext): ValidationDBIOResult =
    DBIO.successful(().success)

  protected def validateThenApply(result: ValidationDBIOResult)(f: => DBIOAction[T, NoStream, Effect.All])(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] =
    validateThenApplyVM(result)(f.map(_.success))

  protected def validateThenApplyVM(result: ValidationDBIOResult)(f: => DBIOAction[ValidationModel, NoStream, Effect.All])(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] = {
    val dbio = result.flatMap {
      case Success(_)     => f
      case e @ Failure(_) => DBIO.successful(e)
    }
    runInTransaction(dbio)
  }

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
  def findByIdQuery(id: T#IdType): AppliedCompiledFunction[T#IdType, Query[Q, T, Seq], Seq[Q#TableElementType]]

  @inline
  override def createDBIO(item: T) =
    tableWithId += item

  @inline
  override def createDBIO(items: Seq[T]) =
    tableWithId ++= items

  @inline
  def mapModel(item: T): T = item

  def recordNotFound(id: T#IdType): ValidationModel =
    recordNotFound("id", id.toString)

  def recordNotFoundAsFuture(id: T#IdType): Future[ValidationModel] =
    Future.successful(recordNotFound(id))

  def create(item: T)(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] = {
    val mappedItem = mapModel(item)
    validateThenApply(validate(mappedItem))(createDBIO(mappedItem))
  }

  def findByIdDBIO(id: T#IdType) =
    findByIdQuery(id).result.headOption

  def findById(id: T#IdType): Future[Option[T]] =
    db.run(findByIdDBIO(id))

  def update(item: T)(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] = {
    val mappedItem = mapModel(item)
    validateThenApplyVM(validate((mappedItem))) {
      findByIdQuery(mappedItem.getId)
        .update(mappedItem)
        .map {
          case 1 => mappedItem.success
          case _ => recordNotFound(item.getId)
        }
    }
  }

  def update(
    id: T#IdType
  )(
    f: T => T
  )(
    implicit
    ec: ExecutionContext,
    m:  Manifest[T]
  ): Future[ValidationModel] =
    findById(id).flatMap {
      case Some(item) => update(f(item))
      case None       => recordNotFoundAsFuture(id)
    }

  def destroy(id: T#IdType)(implicit ec: ExecutionContext) = db.run {
    findByIdQuery(id)
      .delete
      .map {
        case 1 => ().success
        case _ => recordNotFound(id)
      }
  }
}

trait PersistModelWithUuid[T <: models.ModelWithUuid, Q <: Table[T] with PersistTableWithUuidPk] extends PersistModelWithPk[UUID, T, Q] {
  lazy val tableWithId = table returning table.map(_.id) into ((item, _) => item)

  protected val findByIdCompiled = Compiled { id: Rep[UUID] =>
    table.filter(_.id === id).take(1)
  }

  def findByIdQuery(id: UUID) =
    findByIdCompiled(id)
}
