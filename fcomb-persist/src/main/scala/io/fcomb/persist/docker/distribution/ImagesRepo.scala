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
import io.fcomb.FcombPostgresProfile.api._
import io.fcomb.models.acl.{Action, MemberKind, Role}
import io.fcomb.models.common.Slug
import io.fcomb.models.docker.distribution.{Image, ImageVisibilityKind}
import io.fcomb.models.{Organization, OwnerKind, Owner, User, Pagination, PaginationData}
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.persist.{PersistTableWithAutoIntPk, PersistModelWithAutoIntPk, OrganizationGroupsRepo, OrganizationGroupUsersRepo}
import io.fcomb.rpc.docker.distribution.{RepositoryResponse, ImageCreateRequest, ImageUpdateRequest}
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.validations._
import java.time.ZonedDateTime
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

class ImageTable(tag: Tag) extends Table[Image](tag, "dd_images") with PersistTableWithAutoIntPk {
  def name           = column[String]("name")
  def slug           = column[String]("slug")
  def visibilityKind = column[ImageVisibilityKind]("visibility_kind")
  def description    = column[String]("description")
  def createdAt      = column[ZonedDateTime]("created_at")
  def updatedAt      = column[Option[ZonedDateTime]]("updated_at")

  // owner
  def ownerId   = column[Int]("owner_id")
  def ownerKind = column[OwnerKind]("owner_kind")

  def * =
    (id, name, slug, (ownerId, ownerKind), visibilityKind, description, createdAt, updatedAt) <>
      ({
        case (id,
              name,
              slug,
              (ownerId, ownerKind),
              visibilityKind,
              description,
              createdAt,
              updatedAt) =>
          val owner = Owner(ownerId, ownerKind)
          Image(id, name, slug, owner, visibilityKind, description, createdAt, updatedAt)
      }, { img: Image =>
        Some(
          (img.id,
           img.name,
           img.slug,
           (img.owner.id, img.owner.kind),
           img.visibilityKind,
           img.description,
           img.createdAt,
           img.updatedAt))
      })
}

object ImagesRepo extends PersistModelWithAutoIntPk[Image, ImageTable] {
  val table = TableQuery[ImageTable]
  val label = "images"

  private lazy val findIdByNameCompiled = Compiled { name: Rep[String] =>
    table.filter(_.name === name.asColumnOfType[String]("citext")).map(_.pk).take(1)
  }

  def findIdByName(name: String) =
    db.run(findIdByNameCompiled(name).result.headOption)

  lazy val findBySlugCompiled = Compiled { slug: Rep[String] =>
    table.filter(_.slug === slug.asColumnOfType[String]("citext")).take(1)
  }

  def findBySlugWithAcl(slug: String, userId: Int, action: Action)(
      implicit ec: ExecutionContext): Future[Option[Image]] = {
    db.run {
      findBySlugCompiled(slug).result.headOption.flatMap(mapWithAclDBIO(_, userId, action))
    }
  }

  def findByIdWithAcl(id: Int, userId: Int, action: Action)(
      implicit ec: ExecutionContext): Future[Option[Image]] = {
    db.run {
      findByIdCompiled(id).result.headOption.flatMap(mapWithAclDBIO(_, userId, action))
    }
  }

  private def mapWithAclDBIO(imageOpt: Option[Image], userId: Int, action: Action)(
      implicit ec: ExecutionContext) = {
    imageOpt match {
      case Some(image) =>
        image.owner.kind match {
          case OwnerKind.User =>
            if (image.owner.id == userId) DBIO.successful(imageOpt)
            else
              PermissionsRepo
                .isAllowedActionByImageAsUserDBIO(image.getId(), userId, action)
                .map(isAllowed => if (isAllowed) imageOpt else None)
          case OwnerKind.Organization =>
            PermissionsRepo
              .isAllowedActionByImageAsGroupUserDBIO(image.getId(), image.owner.id, userId, action)
              .map(isAllowed => if (isAllowed) imageOpt else None)
        }
      case res => DBIO.successful(res)
    }
  }

  def findIdsByOrganizationIdDBIO(organizationId: Int) = {
    table.filter { q =>
      q.ownerId === organizationId &&
      q.ownerKind === (OwnerKind.Organization: OwnerKind)
    }.map(_.pk)
  }

  private def availableByUserOwnerScopeDBIO(userId: Rep[Int]) = {
    table.filter { t =>
      t.ownerId === userId && t.ownerKind === (OwnerKind.User: OwnerKind)
    }.map(t => (t, Action.Manage: Rep[Action]))
  }

  private def availableByUserPermissionsScopeDBIO(userId: Rep[Int]) = {
    table
      .join(PermissionsRepo.table)
      .on { case (t, pt) => pt.imageId === t.pk }
      .filter {
        case (_, pt) =>
          pt.memberId === userId && pt.memberKind === (MemberKind.User: MemberKind)
      }
      .subquery
      .map { case (t, pt) => (t, pt.action) }
  }

  private def availableByUserOrganizationsScopeDBIO(userId: Rep[Int]) = {
    table
      .join(OrganizationGroupsRepo.table)
      .on {
        case (t, ogt) =>
          ogt.role === (Role.Admin: Role) && t.ownerId === ogt.organizationId &&
            t.ownerKind === (OwnerKind.Organization: OwnerKind)
      }
      .join(OrganizationGroupUsersRepo.table)
      .on { case ((_, ogt), ogut) => ogt.id === ogut.groupId }
      .filter {
        case ((_, _), ogut) => ogut.userId === userId
      }
      .subquery
      .map { case ((t, _), _) => (t, Action.Manage: Rep[Action]) }
  }

  private def availableByUserGroupsScopeDBIO(userId: Rep[Int]) = {
    table
      .join(PermissionsRepo.table)
      .on { case (t, pt) => pt.imageId === t.pk }
      .join(OrganizationGroupUsersRepo.table)
      .on { case (_, gut) => gut.userId === userId }
      .filter {
        case ((_, pt), gut) =>
          pt.memberId === gut.groupId && pt.memberKind === (MemberKind.Group: MemberKind)
      }
      .sortBy {
        case ((t, pt), _) =>
          Case
            .If(pt.action === (Action.Manage: Action))
            .Then(1)
            .If(pt.action === (Action.Write: Action))
            .Then(2)
            .Else(3)
      }
      .subquery
      .map { case ((t, pt), _) => (t, pt.action) }
  }

  private def availableScopeDBIO(userId: Rep[Int]) = {
    availableByUserOwnerScopeDBIO(userId)
      .union(availableByUserPermissionsScopeDBIO(userId))
      .union(availableByUserOrganizationsScopeDBIO(userId))
      .union(availableByUserGroupsScopeDBIO(userId))
      .subquery
  }

  private lazy val findIdByUserIdAndNameCompiled = Compiled {
    (userId: Rep[Int], slug: Rep[String]) =>
      availableScopeDBIO(userId)
        .map(_._1)
        .filter(_.slug === slug.asColumnOfType[String]("citext"))
        .map(_.pk)
  }

  private lazy val findRepositoriesByUserIdCompiled = Compiled {
    (userId: Rep[Int], limit: ConstColumn[Long], id: Rep[Int]) =>
      availableScopeDBIO(userId)
        .map(_._1)
        .filter(_.pk > id)
        .sortBy(_.id.asc)
        .map(_.slug)
        .take(limit)
  }

  val fetchLimit = 64

  def findRepositoriesByUserId(userId: Int, n: Option[Int], last: Option[String])(
      implicit ec: ExecutionContext): Future[(Seq[String], Int, Boolean)] = {
    val limit = n match {
      case Some(v) if v > 0 && v <= fetchLimit => v
      case _                                   => fetchLimit
    }
    val since = last match {
      case Some(slug) =>
        findIdByUserIdAndNameCompiled((userId, slug)).result.headOption.map {
          case Some(id) => id
          case None     => 0
        }
      case None => DBIO.successful(0)
    }
    db.run(for {
        id    <- since
        repos <- findRepositoriesByUserIdCompiled((userId, limit + 1L, id)).result
      } yield repos)
      .fast
      .map(repos => (repos.take(limit), limit, repos.length > limit))
  }

  def create(req: ImageCreateRequest, user: User)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    val owner = Owner(user.getId(), OwnerKind.User)
    create(
      Image(
        id = None,
        name = req.name,
        slug = s"${user.username}/${req.name}",
        owner = owner,
        visibilityKind = req.visibilityKind,
        description = req.description.getOrElse(""),
        createdAt = ZonedDateTime.now,
        updatedAt = None
      ))
  }

  def create(req: ImageCreateRequest, org: Organization)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    val owner = Owner(org.getId(), OwnerKind.Organization)
    create(
      Image(
        id = None,
        name = req.name,
        slug = s"${org.name}/${req.name}",
        owner = owner,
        visibilityKind = req.visibilityKind,
        description = req.description.getOrElse(""),
        createdAt = ZonedDateTime.now,
        updatedAt = None
      ))
  }

  override def createDBIO(item: Image)(implicit ec: ExecutionContext): ModelDBIO = {
    for {
      res <- super.createDBIO(item)
      _ <- item.owner.kind match {
            case OwnerKind.User =>
              PermissionsRepo.createUserOwnerDBIO(res.getId(), item.owner.id, Action.Manage)
            case _ => DBIO.successful(())
          }
    } yield res
  }

  def findBySlugDBIO(slug: Slug) = {
    slug match {
      case Slug.Id(id)     => findByIdCompiled(id)
      case Slug.Name(name) => findBySlugCompiled(name)
    }
  }

  def findBySlug(slug: Slug)(implicit ec: ExecutionContext): Future[Option[Image]] = {
    db.run(findBySlugDBIO(slug).result.headOption)
  }

  def update(id: Int, req: ImageUpdateRequest)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    update(id)(_.copy(description = req.description))
  }

  private def findAvailableByUserIdScopeDBIO(userId: Rep[Int]) = {
    table
      .join(PermissionsRepo.table)
      .on(_.id === _.imageId)
      .filter {
        case (t, pt) =>
          // TODO: add organizations
          pt.memberId === userId && pt.memberKind === (MemberKind.User: MemberKind)
      }
      .map(_._1)
  }

  private lazy val findAvailableByUserIdCompiled = Compiled {
    (userId: Rep[Int], offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
      findAvailableByUserIdScopeDBIO(userId).drop(offset).take(limit)
  }

  private lazy val findAvailableByUserIdTotalCompiled = Compiled { ownerId: Rep[Int] =>
    findAvailableByUserIdScopeDBIO(ownerId).length
  }

  def findAvailableByUserId(userId: Int, p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[RepositoryResponse]] = {
    db.run {
      for {
        images <- findAvailableByUserIdCompiled((userId, p.offset, p.limit)).result
        total  <- findAvailableByUserIdTotalCompiled(userId).result
        data = images.map(ImageHelpers.responseFrom)
      } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit)
    }
  }

  private def findByUserOwnerScopeDBIO(ownerId: Rep[Int], currentUserId: Rep[Int]) = {
    table
      .joinLeft(PermissionsRepo.table)
      .on(_.id === _.imageId)
      .filter {
        case (t, pt) =>
          t.ownerId === ownerId && t.ownerKind === (OwnerKind.User: OwnerKind) &&
            (t.ownerId === currentUserId ||
                  t.visibilityKind === (ImageVisibilityKind.Public: ImageVisibilityKind) ||
                  (pt.map(_.memberId) === currentUserId) &&
                    pt.map(_.memberKind) === (MemberKind.User: MemberKind))
      }
      .map(_._1)
  }

  private lazy val findByUserOwnerCompiled = Compiled {
    (ownerId: Rep[Int], currentUserId: Rep[Int], offset: ConstColumn[Long],
     limit: ConstColumn[Long]) =>
      findByUserOwnerScopeDBIO(ownerId, currentUserId).drop(offset).take(limit)
  }

  private lazy val findByUserOwnerTotalCompiled = Compiled {
    (ownerId: Rep[Int], currentUserId: Rep[Int]) =>
      findByUserOwnerScopeDBIO(ownerId, currentUserId).length
  }

  private def findPublicByOwnerScopeDBIO(ownerId: Rep[Int], ownerKind: Rep[OwnerKind]) = {
    table.filter { q =>
      q.ownerId === ownerId && q.ownerKind === ownerKind &&
      q.visibilityKind === (ImageVisibilityKind.Public: ImageVisibilityKind)
    }
  }

  private lazy val findPublicByOwnerCompiled = Compiled {
    (ownerId: Rep[Int], ownerKind: Rep[OwnerKind], offset: ConstColumn[Long],
     limit: ConstColumn[Long]) =>
      findPublicByOwnerScopeDBIO(ownerId, ownerKind).drop(offset).take(limit)
  }

  private lazy val findPublicByOwnerTotalCompiled = Compiled {
    (ownerId: Rep[Int], ownerKind: Rep[OwnerKind]) =>
      findPublicByOwnerScopeDBIO(ownerId, ownerKind).length
  }

  private def findPublicByOwnerDBIO(ownerId: Int, ownerKind: OwnerKind, p: Pagination)(
      implicit ec: ExecutionContext) = {
    for {
      images <- findPublicByOwnerCompiled((ownerId, ownerKind, p.offset, p.limit)).result
      total  <- findPublicByOwnerTotalCompiled((ownerId, ownerKind)).result
    } yield (images, total)
  }

  def findByUserOwner(userId: Int, currentUserIdOpt: Option[Int], p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[RepositoryResponse]] = {
    val q = currentUserIdOpt match {
      case Some(id) =>
        for {
          images <- findByUserOwnerCompiled((userId, id, p.offset, p.limit)).result
          total  <- findByUserOwnerTotalCompiled((userId, id)).result
        } yield (images, total)
      case _ => findPublicByOwnerDBIO(userId, OwnerKind.User, p)
    }
    db.run(q.map {
      case (images, total) =>
        val data = images.map(ImageHelpers.responseFrom)
        PaginationData(data, total = total, offset = p.offset, limit = p.limit)
    })
  }

  def findByOrganizationOwner(orgId: Int, currentUserIdOpt: Option[Int], p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[RepositoryResponse]] = {
    ??? // TODO
  }

  def updateVisibility(id: Int, visibilityKind: ImageVisibilityKind): Future[_] = {
    db.run {
      table.filter(_.id === id).map(_.visibilityKind).update(visibilityKind)
    }
  }

  def destroyDBIO(id: Int)(implicit ec: ExecutionContext) = {
    for {
      _   <- ImageBlobsRepo.destroyByImageIdDBIO(id)
      res <- super.destroyDBIO(id)
    } yield res
  }

  override def destroy(id: Int)(implicit ec: ExecutionContext) = {
    runInTransaction(TransactionIsolation.Serializable)(destroyDBIO(id))
  }

  private lazy val uniqueNameCompiled = Compiled { (id: Rep[Option[Int]], name: Rep[String]) =>
    exceptIdFilter(id).filter(_.name === name.asColumnOfType[String]("citext")).exists
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
    validate((plainValidations, dbioValidations))
  }
}
