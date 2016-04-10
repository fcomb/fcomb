package io.fcomb.persist.docker.distribution

import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{Image ⇒ MImage}
import io.fcomb.response.DistributionImageCatalog
import io.fcomb.persist._
import io.fcomb.validations._
import scala.concurrent.{ExecutionContext, Future}
import scalaz._, Scalaz._
import slick.jdbc.TransactionIsolation
import java.time.ZonedDateTime

class ImageTable(tag: Tag) extends Table[MImage](tag, "docker_distribution_images") with PersistTableWithAutoLongPk {
  def name = column[String]("name")
  def userId = column[Long]("user_id")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[ZonedDateTime]("updated_at")

  def * =
    (id, name, userId, createdAt, updatedAt) <>
      ((MImage.apply _).tupled, MImage.unapply)
}

object Image extends PersistModelWithAutoLongPk[MImage, ImageTable] {
  val table = TableQuery[ImageTable]

  private val findByNameCompiled = Compiled { name: Rep[String] ⇒
    table
      .filter(_.name.toLowerCase === name.toLowerCase)
      .map(_.id)
      .take(1)
  }

  def findIdOrCreateByName(name: String, userId: Long)(
    implicit
    ec: ExecutionContext
  ): Future[ValidationResult[Long]] = runInTransaction(TransactionIsolation.ReadUncommitted) {
    for {
      _ ← sqlu"LOCK TABLE #${table.baseTableRow.tableName} IN SHARE ROW EXCLUSIVE MODE"
      res ← findByNameCompiled(name).result.headOption.flatMap {
        case Some(idOpt) ⇒ DBIO.successful(idOpt.get.success)
        case None ⇒
          val timeNow = ZonedDateTime.now
          createWithValidationDBIO(MImage(
            name = name,
            userId = userId,
            createdAt = timeNow,
            updatedAt = timeNow
          )).map(_.map(_.getId))
      }
    } yield res
  }

  private val repositoriesCompiled = Compiled {
    table.map(_.name).sortBy(identity)
  }

  def repositories()(
    implicit
    ec:  ExecutionContext
  ) =
    db.run(repositoriesCompiled.result.map(DistributionImageCatalog(_)))

  private val uniqueNameCompiled = Compiled {
    (id: Rep[Option[Long]], name: Rep[String]) ⇒
      notCurrentPkFilter(id).filter { q ⇒
        q.name.toLowerCase === name.toLowerCase
      }.exists
  }

  import Validations._

  override def validate(i: MImage)(
    implicit
    ec: ExecutionContext
  ): ValidationDBIOResult = {
    val plainValidations = validatePlain(
      "name" → List(
        lengthRange(i.name, 1, 255),
        matches(i.name, MImage.nameRegEx, "Invalid name format")
      )
    )
    val dbioValidations = validateDBIO(
      "name" → List(unique(uniqueNameCompiled(i.id, i.name)))
    )
    validate(plainValidations, dbioValidations)
  }
}
