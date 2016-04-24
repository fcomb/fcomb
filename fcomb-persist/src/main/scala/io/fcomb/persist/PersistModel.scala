package io.fcomb.persist

import io.fcomb.{models, validations}
import io.fcomb.validations.{DBIOT, ValidationResult, ValidationResultUnit}
import io.fcomb.Db._
import io.fcomb.RichPostgresDriver.IntoInsertActionComposer
import io.fcomb.RichPostgresDriver.api._
import akka.http.scaladsl.util.FastFuture, FastFuture._
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import cats.data.Validated, cats.std.all._
import cats.syntax.cartesian._
import slick.jdbc.TransactionIsolation
import slick.lifted.AppliedCompiledFunction

trait PersistTypes[T] {
  type ValidationModel = ValidationResult[T]
  type ValidationDBIOResult = DBIOT[ValidationResultUnit]

  def validationError[E](columnName: String, error: String): ValidationResult[E] =
    validations.validationErrors(columnName → error)

  def validationErrorAsFuture[E](columnName: String, error: String): Future[ValidationResult[E]] =
    FastFuture.successful(validationError(columnName, error))

  def recordNotFound[E](columnName: String, id: String): ValidationResult[E] =
    validationError(columnName, s"Not found `$id` record")

  def recordNotFoundAsFuture[E](field: String, id: String): Future[ValidationResult[E]] =
    FastFuture.successful(recordNotFound(field, id))
}

trait PersistModel[T, Q <: Table[T]] extends PersistTypes[T] {
  val table: TableQuery[Q]

  type ModelDBIO = DBIOAction[T, NoStream, Effect.All]
  type ModelDBIOOption = DBIOAction[Option[T], NoStream, Effect.All]

  val validationsOpt = Option.empty[T ⇒ Future[ValidationResult[_]]]

  // @inline
  // private def recoverPersistExceptions(
  //   f: Future[ValidationModel]
  // )(implicit ec: ExecutionContext): Future[ValidationModel] =
  //   f.recover {
  //     case _: RecordNotFound => ("record", "not found").failureNel[T]
  //   }

  @inline
  def runInTransaction[Q](isolation: TransactionIsolation)(
    q: DBIOAction[Q, NoStream, Effect.All]
  )(implicit ec: ExecutionContext): Future[Q] = db.run {
    q.transactionally.withTransactionIsolation(isolation)
  }

  protected def validate(
    t: (ValidationResultUnit, ValidationDBIOResult)
  )(implicit ec: ExecutionContext): ValidationDBIOResult =
    for {
      plainRes ← DBIO.successful(t._1)
      dbioRes ← t._2
    } yield plainRes *> dbioRes

  protected def validate(item: T)(implicit ec: ExecutionContext): ValidationDBIOResult =
    DBIO.successful(Validated.Valid(()))

  protected def validateThenApply(result: ValidationDBIOResult)(
    f: ⇒ DBIOAction[T, NoStream, Effect.All]
  )(
    implicit
    ec: ExecutionContext, m: Manifest[T]
  ): Future[ValidationModel] =
    validateThenApplyVM(result)(f.map(Validated.Valid(_)))

  protected def validateThenApplyDBIO(result: ValidationDBIOResult)(
    f: ⇒ DBIOAction[T, NoStream, Effect.All]
  )(
    implicit
    ec: ExecutionContext,
    m:  Manifest[T]
  ): DBIOAction[ValidationModel, NoStream, Effect.All] =
    validateThenApplyVMDBIO(result)(f.map(Validated.Valid(_)))

  protected def validateThenApplyVMDBIO(result: ValidationDBIOResult)(
    f: ⇒ DBIOAction[ValidationModel, NoStream, Effect.All]
  )(
    implicit
    ec: ExecutionContext,
    m:  Manifest[T]
  ): DBIOAction[ValidationModel, NoStream, Effect.All] = {
    result.flatMap {
      case Validated.Valid(_)       ⇒ f
      case e @ Validated.Invalid(_) ⇒ DBIO.successful(e)
    }
  }

  protected def validateThenApplyVM(result: ValidationDBIOResult)(
    f: ⇒ DBIOAction[ValidationModel, NoStream, Effect.All]
  )(
    implicit
    ec: ExecutionContext,
    m:  Manifest[T]
  ): Future[ValidationModel] =
    runInTransaction(TransactionIsolation.ReadCommitted)(validateThenApplyVMDBIO(result)(f))

  def all() =
    db.run(table.result)

  def allAsStream() =
    db.stream(table.result)

  @inline
  def mapModel(item: T): T = item

  def createDBIO(item: T): ModelDBIO =
    table returning table.map(i ⇒ i) += item.asInstanceOf[Q#TableElementType]

  def createDBIO(items: Seq[T]) =
    table returning table.map(i ⇒ i) ++= items.asInstanceOf[Seq[Q#TableElementType]]

  def createDBIO(itemOpt: Option[T])(implicit ec: ExecutionContext): ModelDBIOOption =
    itemOpt match {
      case Some(item) ⇒ createDBIO(item).map(Some(_))
      case None       ⇒ DBIO.successful(Option.empty[T])
    }

  def createWithValidationDBIO(item: T)(
    implicit
    ec: ExecutionContext,
    m:  Manifest[T]
  ): DBIOAction[ValidationModel, NoStream, Effect.All] = {
    val mappedItem = mapModel(item)
    validateThenApplyDBIO(validate(mappedItem))(createDBIO(mappedItem))
  }

  def create(item: T)(
    implicit
    ec: ExecutionContext,
    m:  Manifest[T]
  ): Future[ValidationModel] =
    runInTransaction(TransactionIsolation.ReadCommitted)(createWithValidationDBIO(item))

  def strictUpdateDBIO[R](res: R)(
    q:     Int,
    error: ValidationResult[R]
  )(
    implicit
    ec: ExecutionContext
  ): ValidationResult[R] =
    if (q == 0) error
    else Validated.Valid(res)

  def strictDestroyDBIO(q: Int, error: ValidationResultUnit)(
    implicit
    ec: ExecutionContext
  ): ValidationResultUnit =
    if (q == 0) error
    else Validated.Valid(())
}

trait PersistTableWithPk { this: Table[_] ⇒
  type Pk

  def id: Rep[Option[Pk]]
  def pk: Rep[Pk]
}

trait PersistTableWithUuidPk extends PersistTableWithPk { this: Table[_] ⇒
  type Pk = UUID

  def id = column[Option[UUID]]("id", O.PrimaryKey)
  def pk = column[UUID]("id", O.PrimaryKey)
}

trait PersistTableWithAutoLongPk extends PersistTableWithPk { this: Table[_] ⇒
  type Pk = Long

  def id = column[Option[Long]]("id", O.AutoInc, O.PrimaryKey)
  def pk = column[Long]("id", O.AutoInc, O.PrimaryKey)
}

trait PersistModelWithPk[T <: models.ModelWithPk, Q <: Table[T] with PersistTableWithPk]
    extends PersistModel[T, Q] {
  val tableWithPk: IntoInsertActionComposer[T, T]

  @inline
  def findByPkQuery(id: T#PkType): AppliedCompiledFunction[T#PkType, Query[Q, T, Seq], Seq[Q#TableElementType]]

  @inline
  override def createDBIO(item: T) =
    tableWithPk += item

  @inline
  override def createDBIO(items: Seq[T]) =
    tableWithPk ++= items

  def recordNotFound[E](id: T#PkType): ValidationResult[E] =
    recordNotFound("id", id.toString)

  def recordNotFoundAsFuture[E](id: T#PkType): Future[ValidationResult[E]] =
    FastFuture.successful(recordNotFound(id))

  def findByPkDBIO(id: T#PkType) =
    findByPkQuery(id).result.headOption

  def findByPk(id: T#PkType): Future[Option[T]] =
    db.run(findByPkDBIO(id))

  def notCurrentPkFilter(id: Rep[Option[T#PkType]]): Query[Q, T, Seq]

  def updateDBIO(item: T)(
    implicit
    ec: ExecutionContext,
    m:  Manifest[T]
  ) =
    findByPkQuery(item.getId)
      .update(item)
      .map(strictUpdateDBIO(item.getId, item))

  def strictUpdateDBIO[R](id: T#PkType, res: R)(q: Int)(
    implicit
    ec: ExecutionContext
  ): ValidationResult[R] =
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
    f: T ⇒ T
  )(
    implicit
    ec: ExecutionContext,
    m:  Manifest[T]
  ): Future[ValidationModel] =
    findByPk(id).flatMap {
      case Some(item) ⇒ update(f(item))
      case None       ⇒ recordNotFoundAsFuture(id)
    }

  def destroy(id: T#PkType)(implicit ec: ExecutionContext) = db.run {
    findByPkQuery(id)
      .delete
      .map(strictDestroyDBIO(id)(_))
  }

  def strictDestroyDBIO(id: T#PkType)(q: Int)(
    implicit
    ec: ExecutionContext
  ): ValidationResultUnit =
    super.strictDestroyDBIO(q, recordNotFound(id))
}

trait PersistModelWithUuidPk[T <: models.ModelWithUuidPk, Q <: Table[T] with PersistTableWithUuidPk]
    extends PersistModelWithPk[T, Q] {
  lazy val tableWithPk = table.returning(table.map(_.id))
    .into((item, _) ⇒ item)

  protected val findByPkCompiled = Compiled { id: Rep[UUID] ⇒
    table.filter(_.id === id)
  }

  def findByPkQuery(id: UUID) =
    findByPkCompiled(id)

  def notCurrentPkFilter(id: Rep[Option[UUID]]) =
    table.filter { q ⇒
      id.flatMap(id ⇒ q.id.map(_ =!= id)).getOrElse(id.isEmpty)
    }
}

trait PersistModelWithAutoLongPk[T <: models.ModelWithAutoLongPk, Q <: Table[T] with PersistTableWithAutoLongPk]
    extends PersistModelWithPk[T, Q] {
  lazy val tableWithPk =
    table.returning(table.map(_.id))
      .into((item, id) ⇒ item.withPk(id.get).asInstanceOf[T])

  protected val findByPkCompiled = Compiled { id: Rep[Long] ⇒
    table.filter(_.id === id)
  }

  def findByPkQuery(id: Long) =
    findByPkCompiled(id)

  def notCurrentPkFilter(id: Rep[Option[Long]]) =
    table.filter { q ⇒
      id.flatMap(id ⇒ q.id.map(_ =!= id)).getOrElse(id.isEmpty)
    }
}
