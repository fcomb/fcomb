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
import io.fcomb.Db.db
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.acl.{Action, SourceKind, MemberKind, Role}
import io.fcomb.models.docker.distribution.{Image, ImageVisibilityKind, ImageCreateRequest}
import io.fcomb.models.{OwnerKind, User, Pagination, PaginationData}
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.persist.{PersistTableWithAutoLongPk, PersistModelWithAutoLongPk, OrganizationGroupsRepo, OrganizationGroupUsersRepo}
import io.fcomb.validations._
import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}

class ImageTable(tag: Tag) extends Table[Image](tag, "dd_images") with PersistTableWithAutoLongPk {
  def name           = column[String]("name")
  def slug           = column[String]("slug")
  def ownerId        = column[Long]("owner_id")
  def ownerKind      = column[OwnerKind]("owner_kind")
  def visibilityKind = column[ImageVisibilityKind]("visibility_kind")
  def description    = column[String]("description")
  def createdAt      = column[ZonedDateTime]("created_at")
  def updatedAt      = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, name, slug, ownerId, ownerKind, visibilityKind, description, createdAt, updatedAt) <>
    ((Image.apply _).tupled, Image.unapply)
}

object ImagesRepo extends PersistModelWithAutoLongPk[Image, ImageTable] {
  val table = TableQuery[ImageTable]
  val label = "images"

  private lazy val findIdByNameCompiled = Compiled { name: Rep[String] =>
    table.filter(_.name.toLowerCase === name.toLowerCase).map(_.pk).take(1)
  }

  def findIdByName(name: String) =
    db.run(findIdByNameCompiled(name).result.headOption)

  val findBySlugCompiled = Compiled { slug: Rep[String] =>
    table.filter { q =>
      q.slug.toLowerCase === slug.toLowerCase
    }.take(1)
  }

  def findBySlugWithAcl(slug: String, userId: Long, action: Action)(
      implicit ec: ExecutionContext): Future[Option[Image]] = {
    db.run {
      findBySlugCompiled(slug).result.headOption.flatMap {
        case res @ Some(image) =>
          image.ownerKind match {
            case OwnerKind.User =>
              if (image.ownerId == userId) DBIO.successful(res)
              else
                PermissionsRepo
                  .isAllowedActionBySourceAsUserDBIO(image.getId,
                                                     SourceKind.DockerDistributionImage,
                                                     userId,
                                                     action)
                  .map { isAllowed =>
                    if (isAllowed) res else None
                  }
            case OwnerKind.Organization =>
              PermissionsRepo
                .isAllowedActionBySourceAsGroupUserDBIO(image.getId,
                                                        SourceKind.DockerDistributionImage,
                                                        image.ownerId,
                                                        userId,
                                                        action)
                .map { isAllowed =>
                  if (isAllowed) res else None
                }
          }
        case res => DBIO.successful(res)
      }
    }.map(identity)
  }

  // TODO: rewrite these join's by readable union's
  private def availableScope(userId: Rep[Long]) = {
    table
      .joinLeft(PermissionsRepo.table)
      .on {
        case (t, pt) =>
          pt.sourceId === t.pk &&
          pt.sourceKind === (SourceKind.DockerDistributionImage: SourceKind)
      }
      .joinLeft(OrganizationGroupUsersRepo.table)
      .on {
        case (_, gut) => gut.userId === userId
      }
      .joinLeft(OrganizationGroupsRepo.table)
      .on {
        case ((_, gut), gt) => gt.id === gut.map(_.groupId)
      }
      .filter {
        case (((t, pt), gut), gt) =>
          (t.ownerId === userId && t.ownerKind === (OwnerKind.User: OwnerKind)) ||
          (pt.map(_.memberId) === userId &&
              pt.map(_.memberKind) === (MemberKind.User: MemberKind)) ||
          (pt.map(_.memberId) === gut.map(_.groupId) &&
              pt.map(_.memberKind) === (MemberKind.Group: MemberKind)) ||
          (gt.map(_.role) === (Role.Admin: Role) && t.ownerId === gt.map(_.organizationId) &&
              t.ownerKind === (OwnerKind.Organization: OwnerKind))
      }
      .map(_._1._1._1)
      .distinct
  }

  private lazy val findIdByUserIdAndNameCompiled = Compiled {
    (userId: Rep[Long], name: Rep[String]) =>
      availableScope(userId).filter(_.name === name).map(_.pk)
  }

  private lazy val findRepositoriesByUserIdCompiled = Compiled {
    (userId: Rep[Long], limit: ConstColumn[Long], id: Rep[Long]) =>
      availableScope(userId).filter(_.pk > id).sortBy(_.id.asc).map(_.name).take(limit)
  }

  val fetchLimit = 64

  def findRepositoriesByUserId(userId: Long, n: Option[Int], last: Option[String])(
      implicit ec: ExecutionContext): Future[(Seq[String], Int, Boolean)] = {
    val limit = n match {
      case Some(v) if v > 0 && v <= fetchLimit => v
      case _                                   => fetchLimit
    }
    val since = last match {
      case Some(imageName) =>
        findIdByUserIdAndNameCompiled((userId, imageName)).result.headOption.map {
          case Some(id) => id
          case None     => 0L
        }
      case None => DBIO.successful(0L)
    }
    db.run(for {
      id <- since
      repositories <- findRepositoriesByUserIdCompiled(
                       (userId, limit + 1L, id)
                     ).result
    } yield repositories)
      .fast
      .map { repositories =>
        (repositories.take(limit), limit, repositories.length > limit)
      }
  }

  def createByRequest(req: ImageCreateRequest, user: User)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    create(
      Image(
        id = None,
        name = req.name,
        slug = s"${user.username}/${req.name}",
        ownerId = user.getId,
        ownerKind = OwnerKind.User,
        visibilityKind = req.visibilityKind,
        description = req.description.getOrElse(""),
        createdAt = ZonedDateTime.now,
        updatedAt = None
      ))
  }

  private def findByUserOwnerScope(userId: Rep[Long]) =
    table.filter { q =>
      q.ownerId === userId && q.ownerKind === (OwnerKind.User: OwnerKind)
    }

  private lazy val findByUserOwnerTotalCompiled = Compiled { userId: Rep[Long] =>
    findByUserOwnerScope(userId).length
  }

  private lazy val findByUserOwnerWithPaginationCompiled = Compiled {
    (userId: Rep[Long], offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
      findByUserOwnerScope(userId).drop(offset).take(limit)
  }

  def findByUserOwnerWithPagination(userId: Long, pg: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[Image]] = {
    db.run {
      for {
        data  <- findByUserOwnerWithPaginationCompiled((userId, pg.offset, pg.limit)).result
        total <- findByUserOwnerTotalCompiled(userId).result
      } yield PaginationData(data, total = total, offset = pg.offset, limit = pg.limit)
    }
  }

  private val uniqueNameCompiled = Compiled { (id: Rep[Option[Long]], name: Rep[String]) =>
    notCurrentPkFilter(id).filter { q =>
      q.name.toLowerCase === name.toLowerCase
    }.exists
  }

  import Validations._

  override def validate(i: Image)(
      implicit ec: ExecutionContext
  ): ValidationDBIOResult = {
    val plainValidations = validatePlain(
      "name" -> List(
        lengthRange(i.name, 1, 255),
        matches(i.name, Image.nameRegEx, "Invalid name format")
      )
    )
    val dbioValidations = validateDBIO(
      "name" -> List(unique(uniqueNameCompiled((i.id, i.name))))
    )
    validate(plainValidations, dbioValidations)
  }
}
