package io.fcomb.persist

import io.fcomb.{models, validations}
import io.fcomb.validations.{DBIOT, ValidationResult, ValidationResultUnit}
import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.IntoInsertActionComposer
import io.fcomb.RichPostgresDriver.api._
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future, blocking}
import scalaz._
import scalaz.Scalaz._
import slick.jdbc.TransactionIsolation
import slick.lifted.AppliedCompiledFunction

trait PersistTypes[T] {
  type ValidationModel = validations.ValidationResult[T]
  type ValidationDBIOResult = DBIOT[ValidationResultUnit]

  def validationError[E](columnName: String, error: String): validations.ValidationResult[E] =
    validations.validationErrors(columnName -> error)

  def validationErrorAsFuture[E](columnName: String, error: String): Future[validations.ValidationResult[E]] =
    Future.successful(validationError(columnName, error))

  def recordNotFound[E](columnName: String, id: String): validations.ValidationResult[E] =
    validationError(columnName, s"Not found `$id` record")

  def recordNotFoundAsFuture[E](field: String, id: String): Future[validations.ValidationResult[E]] =
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

  protected def validate(t: (ValidationResultUnit, ValidationDBIOResult))(implicit ec: ExecutionContext): ValidationDBIOResult =
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
      case Success(_) => f
      case e @ Failure(_) => DBIO.successful(e)
    }
    runInTransaction(dbio)
  }

  def all() =
    db.run(table.result)

  def allAsStream() =
    db.stream(table.result)

  @inline
  def mapModel(item: T): T = item

  def createDBIO(item: T): ModelDBIO =
    table returning table.map(i => i) += item.asInstanceOf[Q#TableElementType]

  def createDBIO(items: Seq[T]) =
    table returning table.map(i => i) ++= items.asInstanceOf[Seq[Q#TableElementType]]

  def createDBIO(itemOpt: Option[T])(implicit ec: ExecutionContext): ModelDBIOOption =
    itemOpt match {
      case Some(item) => createDBIO(item).map(Some(_))
      case None => DBIO.successful(Option.empty[T])
    }

  def create(item: T)(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] = {
    val mappedItem = mapModel(item)
    validateThenApply(validate(mappedItem))(createDBIO(mappedItem))
  }

  def strictUpdateDBIO[R](res: R)(
    q: Int,
    error: validations.ValidationResult[R]
  )(
    implicit
    ec: ExecutionContext
  ): validations.ValidationResult[R] = q match {
    case 1 => res.success
    case _ => error
  }

  def strictDestroyDBIO(q: Int, error: validations.ValidationResultUnit)(
    implicit
    ec: ExecutionContext
  ): validations.ValidationResultUnit = q match {
    case 0 => error
    case _ => ().success
  }
}

trait PersistTableWithPk[T] { this: Table[_] =>
  def id: Rep[Option[T]]
}

trait PersistTableWithUuidPk extends PersistTableWithPk[UUID] { this: Table[_] =>
  def id = column[Option[UUID]]("id", O.PrimaryKey)
}

trait PersistTableWithAutoLongPk extends PersistTableWithPk[Long] { this: Table[_] =>
  def id = column[Option[Long]]("id", O.AutoInc, O.PrimaryKey)
}

trait PersistModelWithPk[Id, T <: models.ModelWithPk[Id], Q <: Table[T] with PersistTableWithPk[Id]] extends PersistModel[T, Q] {
  val tableWithPk: IntoInsertActionComposer[T, T]

  @inline
  def findByPkQuery(id: T#PkType): AppliedCompiledFunction[T#PkType, Query[Q, T, Seq], Seq[Q#TableElementType]]

  @inline
  override def createDBIO(item: T) =
    tableWithPk += item

  @inline
  override def createDBIO(items: Seq[T]) =
    tableWithPk ++= items

  def recordNotFound[E](id: T#PkType): validations.ValidationResult[E] =
    recordNotFound("id", id.toString)

  def recordNotFoundAsFuture[E](id: T#PkType): Future[validations.ValidationResult[E]] =
    Future.successful(recordNotFound(id))

  def findByPkDBIO(id: T#PkType) =
    findByPkQuery(id).result.headOption

  def findByPk(id: T#PkType): Future[Option[T]] =
    db.run(findByPkDBIO(id))

  def notCurrentPkFilter(id: Rep[Option[T#PkType]]): Query[Q, T, Seq]

  def updateDBIO(item: T)(
    implicit
    ec: ExecutionContext,
    m: Manifest[T]
  ) =
    findByPkQuery(item.getId)
      .update(item)
      .map(strictUpdateDBIO(item.getId, item))

  def strictUpdateDBIO[R](id: T#PkType, res: R)(q: Int)(
    implicit
    ec: ExecutionContext
  ): validations.ValidationResult[R] =
    super.strictUpdateDBIO(res)(q, recordNotFound(id))

  def update(item: T)(implicit ec: ExecutionContext, m: Manifest[T]): Future[ValidationModel] = {
    val mappedItem = mapModel(item)
    validateThenApplyVM(validate((mappedItem))) {
      updateDBIO(mappedItem)
    }
  }

  def update(
    id: T#PkType
  )(
    f: T => T
  )(
    implicit
    ec: ExecutionContext,
    m: Manifest[T]
  ): Future[ValidationModel] =
    findByPk(id).flatMap {
      case Some(item) => update(f(item))
      case None => recordNotFoundAsFuture(id)
    }

  def destroy(id: T#PkType)(implicit ec: ExecutionContext) = db.run {
    findByPkQuery(id)
      .delete
      .map(strictDestroyDBIO(id)(_))
  }

  def strictDestroyDBIO(id: T#PkType)(q: Int)(
    implicit
    ec: ExecutionContext
  ): validations.ValidationResultUnit =
    super.strictDestroyDBIO(q, recordNotFound(id))
}

trait PersistModelWithUuidPk[T <: models.ModelWithUuidPk, Q <: Table[T] with PersistTableWithUuidPk] extends PersistModelWithPk[UUID, T, Q] {
  lazy val tableWithPk = table.returning(table.map(_.id))
    .into((item, _) => item)

  protected val findByPkCompiled = Compiled { id: Rep[UUID] =>
    table.filter(_.id === id)
  }

  def findByPkQuery(id: UUID) =
    findByPkCompiled(id)

  def notCurrentPkFilter(id: Rep[Option[UUID]]) =
    table.filter { q =>
      id.flatMap(id => q.id.map(_ =!= id)).getOrElse(id.isEmpty)
    }
}

trait PersistModelWithAutoLongPk[T <: models.ModelWithAutoLongPk, Q <: Table[T] with PersistTableWithAutoLongPk] extends PersistModelWithPk[Long, T, Q] {
  lazy val tableWithPk =
    table.returning(table.map(_.id))
      .into((item, id) => item.withPk(id.get).asInstanceOf[T])

  protected val findByPkCompiled = Compiled { id: Rep[Long] =>
    table.filter(_.id === id)
  }

  def findByPkQuery(id: Long) =
    findByPkCompiled(id)

  def notCurrentPkFilter(id: Rep[Option[Long]]) =
    table.filter { q =>
      id.flatMap(id => q.id.map(_ =!= id)).getOrElse(id.isEmpty)
    }
}
