/*
 * Copyright 2017 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.persist

import cats.data.Validated
import cats.syntax.cartesian._
import cats.instances.all._
import io.fcomb.PostgresProfile.IntoInsertActionComposer
import io.fcomb.PostgresProfile.api._
import io.fcomb.validation.{DBIOT, ValidationResult, ValidationResultUnit}
import io.fcomb.{models, validation}
import java.util.UUID
import scala.concurrent.ExecutionContext
import slick.jdbc.TransactionIsolation
import slick.lifted.AppliedCompiledFunction

trait PersistTypes[T] {
  type ValidationModel      = ValidationResult[T]
  type ValidationDBIOResult = DBIOT[ValidationResultUnit]

  def validationError[E](columnName: String, error: String): ValidationResult[E] =
    validation.validationErrors(columnName -> error)

  def validationErrorDBIO[E](columnName: String, error: String): DBIOT[ValidationResult[E]] =
    DBIO.successful(validationError(columnName, error))

  def recordNotFound[E](columnName: String, id: String): ValidationResult[E] =
    validationError(columnName, s"Not found `$id` record")

  def recordNotFoundDBIO[E](field: String, id: String): DBIO[ValidationResult[E]] =
    DBIO.successful(recordNotFound(field, id))
}

trait PersistModel[T, Q <: Table[T]] extends PersistTypes[T] {
  type ModelType       = T
  type TableType       = Q
  type QueryType       = Query[TableType, ModelType, Seq]
  type ModelDBIO       = DBIOAction[T, NoStream, Effect.All]
  type ModelDBIOOption = DBIOAction[Option[T], NoStream, Effect.All]

  val table: TableQuery[Q]

  val validationOpt = Option.empty[T => DBIO[ValidationResult[_]]]

  // private def recoverPersistExceptions(
  //   f: DBIO[ValidationModel]
  // )(implicit ec: ExecutionContext): DBIO[ValidationModel] =
  //   f.recover {
  //     case _: RecordNotFound => ("record", "not found").failureNel[T]
  //   }

  def runInTransaction[B](isolation: TransactionIsolation)(q: DBIOAction[B, NoStream, Effect.All])(
      implicit ec: ExecutionContext): DBIO[B] =
    q.transactionally.withTransactionIsolation(isolation)

  protected def validate(t: (ValidationResultUnit, ValidationDBIOResult))(
      implicit ec: ExecutionContext): ValidationDBIOResult =
    for {
      plainRes <- DBIO.successful(t._1)
      dbioRes  <- t._2
    } yield plainRes *> dbioRes

  protected def validate(item: T)(implicit ec: ExecutionContext): ValidationDBIOResult =
    DBIO.successful(Validated.Valid(()))

  protected def validateThenApply(result: ValidationDBIOResult)(
      f: => DBIOAction[T, NoStream, Effect.All])(
      implicit ec: ExecutionContext): DBIO[ValidationModel] =
    validateThenApplyVM(result)(f.map(Validated.Valid(_)))

  protected def validateThenApplyDBIO(result: ValidationDBIOResult)(
      f: => DBIOAction[T, NoStream, Effect.All])(
      implicit ec: ExecutionContext): DBIOAction[ValidationModel, NoStream, Effect.All] =
    validateThenApplyVMDBIO(result)(f.map(Validated.Valid(_)))

  protected def validateThenApplyVMDBIO(result: ValidationDBIOResult)(
      f: => DBIOAction[ValidationModel, NoStream, Effect.All])(
      implicit ec: ExecutionContext): DBIOAction[ValidationModel, NoStream, Effect.All] =
    result.flatMap {
      case Validated.Valid(_)       => f
      case e @ Validated.Invalid(_) => DBIO.successful(e)
    }

  protected def validateThenApplyVM(result: ValidationDBIOResult)(
      f: => DBIOAction[ValidationModel, NoStream, Effect.All])(
      implicit ec: ExecutionContext): DBIO[ValidationModel] =
    runInTransaction(TransactionIsolation.ReadCommitted)(validateThenApplyVMDBIO(result)(f))

  def all() = table.result

  def mapModel(item: T): T = item

  def createDBIO(item: T)(implicit ec: ExecutionContext): ModelDBIO =
    table returning table.map(identity) += item.asInstanceOf[Q#TableElementType]

  def createDBIO(items: Seq[T]) =
    table returning table.map(identity) ++= items.asInstanceOf[Seq[Q#TableElementType]]

  def createDBIO(itemOpt: Option[T])(implicit ec: ExecutionContext): ModelDBIOOption =
    itemOpt match {
      case Some(item) => createDBIO(item).map(Some(_))
      case None       => DBIO.successful(Option.empty[T])
    }

  def createWithValidationDBIO(item: T)(
      implicit ec: ExecutionContext): DBIOAction[ValidationModel, NoStream, Effect.All] = {
    val mappedItem = mapModel(item)
    validateThenApplyDBIO(validate(mappedItem))(createDBIO(mappedItem))
  }

  def create(item: T)(implicit ec: ExecutionContext): DBIO[ValidationModel] =
    runInTransaction(TransactionIsolation.ReadCommitted)(
      createWithValidationDBIO(item)
    )

  def strictUpdateDBIO[R](res: R)(q: Int, error: ValidationResult[R])(
      implicit ec: ExecutionContext): ValidationResult[R] =
    if (q == 0) error
    else Validated.Valid(res)

  def destroyDBIO(q: Int, error: ValidationResultUnit)(
      implicit ec: ExecutionContext): ValidationResultUnit =
    if (q == 0) error
    else Validated.Valid(())
}

trait PersistTableWithPk { this: Table[_] =>
  type Pk

  def id: Rep[Pk]
}

trait PersistTableWithUuidPk extends PersistTableWithPk { this: Table[_] =>
  type Pk = UUID

  def id = column[UUID]("id", O.PrimaryKey)
}

trait PersistTableWithAutoIntPk extends PersistTableWithPk { this: Table[_] =>
  type Pk = Int

  def id = column[Int]("id", O.AutoInc, O.PrimaryKey)
}

trait PersistModelWithPk[T <: models.ModelWithPk, Q <: Table[T] with PersistTableWithPk]
    extends PersistModel[T, Q] {
  val tableWithPk: IntoInsertActionComposer[T, T]

  def findByIdQuery(
      id: T#PkType): AppliedCompiledFunction[T#PkType, Query[Q, T, Seq], Seq[Q#TableElementType]]

  override def createDBIO(item: T)(implicit ec: ExecutionContext): ModelDBIO =
    tableWithPk += item

  override def createDBIO(items: Seq[T]) =
    tableWithPk ++= items

  def recordNotFound[E](id: T#PkType): ValidationResult[E] =
    recordNotFound("id", id.toString)

  def recordNotFoundDBIO[E](id: T#PkType): DBIO[ValidationResult[E]] =
    DBIO.successful(recordNotFound(id))

  def findById(id: T#PkType) =
    findByIdQuery(id).result.headOption

  def exceptIdFilter(id: Rep[Option[T#PkType]]): Query[Q, T, Seq]

  def updateDBIO(item: T)(
      implicit ec: ExecutionContext): DBIOAction[ValidationModel, NoStream, Effect.All] =
    findByIdQuery(item.getId()).update(item).map(strictUpdateDBIO(item.getId(), item))

  def updateDBIOWithValidation(item: T)(implicit ec: ExecutionContext): DBIO[ValidationModel] = {
    val mappedItem = mapModel(item)
    validateThenApplyVMDBIO(validate(mappedItem))(updateDBIO(mappedItem))
  }

  // TODO: replace
  def updateDBIO(id: T#PkType)(f: T => T)(
      implicit ec: ExecutionContext): DBIOAction[ValidationModel, NoStream, Effect.All] =
    findByIdQuery(id).result.headOption.flatMap {
      case Some(item) => updateDBIOWithValidation(item)
      case _          => DBIO.successful(recordNotFound(id))
    }

  def strictUpdateDBIO[R](id: T#PkType, res: R)(q: Int)(
      implicit ec: ExecutionContext): ValidationResult[R] =
    super.strictUpdateDBIO(res)(q, recordNotFound(id))

  def update(item: T)(implicit ec: ExecutionContext): DBIO[ValidationModel] = {
    val mappedItem = mapModel(item)
    validateThenApplyVM(validate(mappedItem)) {
      updateDBIO(mappedItem)
    }
  }

  def update(id: T#PkType)(f: T => T)(implicit ec: ExecutionContext): DBIO[ValidationModel] =
    findById(id).flatMap {
      case Some(item) => update(f(item))
      case None       => recordNotFoundDBIO(id)
    }

  def destroy(id: T#PkType)(implicit ec: ExecutionContext): DBIO[ValidationResultUnit] =
    findByIdQuery(id).delete.map(destroyDBIO(_, recordNotFound(id)))
}

trait PersistModelWithUuidPk[
    T <: models.ModelWithUuidPk, Q <: Table[T] with PersistTableWithUuidPk]
    extends PersistModelWithPk[T, Q] {
  lazy val tableWithPk = table.returning(table.map(_.id)).into((item, _) => item)

  protected lazy val findByIdC = Compiled { id: Rep[UUID] =>
    table.filter(_.id === id)
  }

  def findByIdQuery(id: UUID) =
    findByIdC(id)

  def exceptIdFilter(id: Rep[Option[UUID]]) =
    table.filter(_.id =!= id || id.isEmpty)
}

trait PersistModelWithAutoIntPk[
    T <: models.ModelWithAutoIntPk, Q <: Table[T] with PersistTableWithAutoIntPk]
    extends PersistModelWithPk[T, Q] {
  lazy val tableWithPk =
    table.returning(table.map(_.id)).into((item, id) => item.withId(id).asInstanceOf[T])

  protected lazy val findByIdC = Compiled { id: Rep[Int] =>
    table.filter(_.id === id)
  }

  def findByIdQuery(id: Int) =
    findByIdC(id)

  def exceptIdFilter(id: Rep[Option[Int]]) =
    table.filter(_.id =!= id || id.isEmpty)
}
