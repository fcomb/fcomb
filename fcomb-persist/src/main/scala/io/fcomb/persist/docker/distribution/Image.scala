package io.fcomb.persist.docker.distribution

import cats.data.Validated
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{Image ⇒ MImage}
import io.fcomb.persist._
import io.fcomb.response.DistributionImageCatalog
import io.fcomb.validations._
import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

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

  private val findIdByNameCompiled = Compiled { name: Rep[String] ⇒
    table
      .filter(_.name.toLowerCase === name.toLowerCase)
      .map(_.pk)
      .take(1)
  }

  def findIdByName(name: String) =
    db.run(findIdByNameCompiled(name).result.headOption)

  private val findByImageAndUserIdCompiled = Compiled {
    (name: Rep[String], userId: Rep[Long]) ⇒
      table
        .filter { q ⇒
          q.name.toLowerCase === name.toLowerCase &&
            q.userId === userId
        }
        .take(1)
  }

  def findByImageAndUserId(name: String, userId: Long) =
    db.run(findByImageAndUserIdCompiled((name, userId)).result.headOption)

  private val findIdByImageAndUserIdCompiled = Compiled {
    (name: Rep[String], userId: Rep[Long]) ⇒
      table
        .filter { q ⇒
          q.name.toLowerCase === name.toLowerCase &&
            q.userId === userId
        }
        .map(_.pk)
        .take(1)
  }

  def findIdOrCreateByName(name: String, userId: Long)(
    implicit
    ec: ExecutionContext
  ): Future[ValidationResult[Long]] =
    runInTransaction(TransactionIsolation.ReadUncommitted) {
      for {
        _ ← sqlu"LOCK TABLE #${table.baseTableRow.tableName} IN SHARE ROW EXCLUSIVE MODE"
        res ← findIdByImageAndUserIdCompiled((name, userId)).result.headOption.flatMap {
          case Some(id) ⇒ DBIO.successful(Validated.Valid(id))
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

  private val repositoriesByUserIdCompiled = Compiled { userId: Rep[Long] ⇒
    table
      .filter(_.userId === userId)
      .map(_.name)
      .sortBy(identity)
  }

  def repositoriesByUserId(userId: Long)(
    implicit
    ec: ExecutionContext
  ) =
    db.run(repositoriesByUserIdCompiled(userId).result.map(DistributionImageCatalog(_)))

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
