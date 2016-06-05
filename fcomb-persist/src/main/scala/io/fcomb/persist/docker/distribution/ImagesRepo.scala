/*
 * Copyright 2016 fcomb. <https://fcomb.io>
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

package io.fcomb.persist.docker.distribution

import akka.http.scaladsl.util.FastFuture, FastFuture._
import cats.data.Validated
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.Image
import io.fcomb.persist._
import io.fcomb.validations._
import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

class ImageTable(tag: Tag) extends Table[Image](tag, "dd_images") with PersistTableWithAutoLongPk {
  def name      = column[String]("name")
  def userId    = column[Long]("user_id")
  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, name, userId, createdAt, updatedAt) <>
    ((Image.apply _).tupled, Image.unapply)
}

object ImagesRepo extends PersistModelWithAutoLongPk[Image, ImageTable] {
  val table = TableQuery[ImageTable]

  private val findIdByNameCompiled = Compiled { name: Rep[String] ⇒
    table.filter(_.name.toLowerCase === name.toLowerCase).map(_.pk).take(1)
  }

  def findIdByName(name: String) =
    db.run(findIdByNameCompiled(name).result.headOption)

  val findByImageAndUserIdCompiled = Compiled { (name: Rep[String], userId: Rep[Long]) ⇒
    table.filter { q ⇒
      q.name.toLowerCase === name.toLowerCase && q.userId === userId
    }.take(1)
  }

  def findByImageAndUserId(name: String, userId: Long) =
    db.run(findByImageAndUserIdCompiled((name, userId)).result.headOption)

  val findIdAndUserIdByImageCompiled = Compiled { (name: Rep[String], userId: Rep[Long]) ⇒
    table.filter { q ⇒
      q.name.toLowerCase === name.toLowerCase && q.userId === userId
    }.map(t ⇒ (t.pk, t.userId)).take(1)
  }

  def findIdOrCreateByNameDBIO(
      name: String,
      userId: Long
  )(implicit ec: ExecutionContext) =
    for {
      _ ← sqlu"LOCK TABLE #${table.baseTableRow.tableName} IN SHARE ROW EXCLUSIVE MODE"
      res ← findIdAndUserIdByImageCompiled((name, userId)).result.headOption.flatMap {
             case Some((id, imageUserId)) ⇒
               if (imageUserId == userId) DBIO.successful(Validated.Valid(id))
               else
                 DBIO.successful(
                     validationError(
                         "userId",
                         "insufficient permissions"
                     )) // TODO
             case None ⇒
               val timeNow = ZonedDateTime.now
               createWithValidationDBIO(
                   Image(
                       name = name,
                       userId = userId,
                       createdAt = timeNow,
                       updatedAt = None
                   )
               ).map(_.map(_.getId))
           }
    } yield res

  def findIdOrCreateByName(name: String, userId: Long)(
      implicit ec: ExecutionContext
  ): Future[ValidationResult[Long]] =
    runInTransaction(TransactionIsolation.ReadUncommitted) {
      findIdOrCreateByNameDBIO(name, userId)
    }

  private val findIdByUserIdAndNameCompiled = Compiled { (userId: Rep[Long], name: Rep[String]) ⇒
    table.filter { q ⇒
      q.userId === userId && q.name === name
    }.map(_.pk)
  }

  private val findRepositoriesByUserIdCompiled = Compiled {
    (userId: Rep[Long], limit: ConstColumn[Long], id: Rep[Long]) ⇒
      table.filter { q ⇒
        q.userId === userId && q.pk > id
      }.sortBy(_.id.asc).map(_.name).take(limit)
  }

  val fetchLimit = 256

  def findRepositoriesByUserId(
      userId: Long,
      n: Option[Int],
      last: Option[String]
  )(
      implicit ec: ExecutionContext
  ): Future[(Seq[String], Int, Boolean)] = {
    val limit = n match {
      case Some(v) if v > 0 && v <= fetchLimit ⇒ v
      case _                                   ⇒ fetchLimit
    }
    val since = last match {
      case Some(imageName) ⇒
        findIdByUserIdAndNameCompiled((userId, imageName)).result.headOption.map {
          case Some(id) ⇒ id
          case None     ⇒ 0L
        }
      case None ⇒ DBIO.successful(0L)
    }
    db.run(for {
        id ← since
        repositories ← findRepositoriesByUserIdCompiled(
                          (userId, limit + 1L, id)
                      ).result
      } yield repositories)
      .fast
      .map { repositories ⇒
        (repositories.take(limit), limit, repositories.length > limit)
      }
  }

  private val uniqueNameCompiled = Compiled { (id: Rep[Option[Long]], name: Rep[String]) ⇒
    notCurrentPkFilter(id).filter { q ⇒
      q.name.toLowerCase === name.toLowerCase
    }.exists
  }

  import Validations._

  override def validate(i: Image)(
      implicit ec: ExecutionContext
  ): ValidationDBIOResult = {
    val plainValidations = validatePlain(
        "name" → List(
            lengthRange(i.name, 1, 255),
            matches(i.name, Image.nameRegEx, "Invalid name format")
        )
    )
    val dbioValidations = validateDBIO(
        "name" → List(unique(uniqueNameCompiled(i.id, i.name)))
    )
    validate(plainValidations, dbioValidations)
  }
}
