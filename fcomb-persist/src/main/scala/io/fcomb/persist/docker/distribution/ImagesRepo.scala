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
import cats.data.Xor
import cats.syntax.eq._
import io.fcomb.Db.db
import io.fcomb.FcombPostgresProfile.api._
import io.fcomb.models.acl.{Action, MemberKind, Role}
import io.fcomb.models.common.Slug
import io.fcomb.models.docker.distribution.{Image, ImageVisibilityKind}
import io.fcomb.models.{Organization, Owner, OwnerKind, Pagination, PaginationData, User}
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.persist.{
  EventsRepo,
  OrganizationGroupUsersRepo,
  OrganizationGroupsRepo,
  PersistModelWithAutoIntPk,
  PersistTableWithAutoIntPk
}
import io.fcomb.rpc.docker.distribution.{
  ImageCreateRequest,
  ImageUpdateRequest,
  RepositoryResponse
}
import io.fcomb.rpc.helpers.docker.distribution.ImageHelpers
import io.fcomb.validations._
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

class ImageTable(tag: Tag) extends Table[Image](tag, "dd_images") with PersistTableWithAutoIntPk {
  def name           = column[String]("name")
  def slug           = column[String]("slug")
  def visibilityKind = column[ImageVisibilityKind]("visibility_kind")
  def description    = column[String]("description")
  def createdBy      = column[Int]("created_by")
  def createdAt      = column[OffsetDateTime]("created_at")
  def updatedAt      = column[Option[OffsetDateTime]]("updated_at")

  // owner
  def ownerId   = column[Int]("owner_id")
  def ownerKind = column[OwnerKind]("owner_kind")

  def * =
    (id.?,
     name,
     slug,
     (ownerId, ownerKind),
     visibilityKind,
     description,
     createdBy,
     createdAt,
     updatedAt) <>
      ({
        case (id,
              name,
              slug,
              (ownerId, ownerKind),
              visibilityKind,
              description,
              createdBy,
              createdAt,
              updatedAt) =>
          val owner = Owner(ownerId, ownerKind)
          Image(id,
                name,
                slug,
                owner,
                visibilityKind,
                description,
                createdBy,
                createdAt,
                updatedAt)
      }, { img: Image =>
        Some(
          (img.id,
           img.name,
           img.slug,
           (img.owner.id, img.owner.kind),
           img.visibilityKind,
           img.description,
           img.createdByUserId,
           img.createdAt,
           img.updatedAt))
      })
}

object ImagesRepo extends PersistModelWithAutoIntPk[Image, ImageTable] {
  val table = TableQuery[ImageTable]
  val label = "images"

  private lazy val findBySlugCompiled = Compiled { slug: Rep[String] =>
    table.filter(_.slug === slug.asColumnOfType[String]("citext")).take(1)
  }

  def findBySlugDBIO(slug: Slug) = {
    val q = slug match {
      case Slug.Id(id)     => findByIdCompiled(id)
      case Slug.Name(name) => findBySlugCompiled(name)
    }
    q.result.headOption
  }

  def findBySlug(slug: Slug)(implicit ec: ExecutionContext): Future[Option[Image]] = {
    db.run(findBySlugDBIO(slug))
  }

  /** Returns [[cats.data.Xor.Left]] if forbidden and [[cats.data.Xor.Right]] if image found or not */
  type ImageAclResult = Xor[Unit, Option[(Image, Action)]]

  def findBySlugWithAcl(slug: Slug, userId: Int, action: Action)(
      implicit ec: ExecutionContext): Future[ImageAclResult] = {
    db.run(findBySlugDBIO(slug).flatMap(mapWithAclDBIO(_, userId, action)))
  }

  private def mapWithAclDBIO(imageOpt: Option[Image], userId: Int, action: Action)(
      implicit ec: ExecutionContext): DBIOAction[ImageAclResult, NoStream, Effect.Read] = {
    imageOpt match {
      case Some(image) =>
        if (image.visibilityKind === ImageVisibilityKind.Public && action === Action.Read)
          DBIO.successful(Xor.Right(Some((image, Action.Read))))
        else
          image.owner.kind match {
            case OwnerKind.User =>
              if (image.owner.id == userId)
                DBIO.successful(Xor.Right(Some((image, Action.Manage))))
              else
                PermissionsRepo
                  .findActionByImageAndUserDBIO(image.getId(), userId)
                  .map(checkAcl(_, action, image))
            case OwnerKind.Organization =>
              PermissionsRepo
                .findActionByImageAsGroupUserDBIO(image.getId(), image.owner.id, userId)
                .map(checkAcl(_, action, image))
          }
      case _ => DBIO.successful(Xor.Right(None))
    }
  }

  private def checkAcl(userActionOpt: Option[Action],
                       action: Action,
                       image: Image): ImageAclResult = {
    userActionOpt match {
      case Some(res) =>
        if (res.can(action)) Xor.Right(Some((image, res)))
        else Xor.Left(())
      case _ => Xor.Left(())
    }
  }

  def findIdsByOrganizationIdDBIO(organizationId: Int) = {
    table.filter { q =>
      q.ownerId === organizationId &&
      q.ownerKind === (OwnerKind.Organization: OwnerKind)
    }.map(_.id)
  }

  private def availableByUserOwnerDBIO(userId: Rep[Int]) = {
    table
      .filter(t => t.ownerId === userId && t.ownerKind === (OwnerKind.User: OwnerKind))
      .map(t => (t, (Action.Manage: Rep[Action]).asColumnOf[Action]))
  }

  private def availableByUserPermissionsDBIO(userId: Rep[Int]) = {
    table
      .join(PermissionsRepo.table)
      .on { case (t, pt) => pt.imageId === t.id }
      .filter {
        case (_, pt) =>
          pt.memberId === userId && pt.memberKind === (MemberKind.User: MemberKind)
      }
      .subquery
      .map { case (t, pt) => (t, pt.action) }
  }

  /** Returns organization images scope and members with admin role */
  def organizationAdminsScope = {
    table
      .join(OrganizationGroupsRepo.table)
      .on {
        case (t, ogt) =>
          ogt.role === (Role.Admin: Role) && t.ownerId === ogt.organizationId &&
            t.ownerKind === (OwnerKind.Organization: OwnerKind)
      }
      .join(OrganizationGroupUsersRepo.table)
      .on { case ((_, ogt), ogut) => ogt.id === ogut.groupId }
  }

  private def availableByUserOrganizationsDBIO(userId: Rep[Int]) = {
    organizationAdminsScope.filter {
      case ((_, _), ogut) => ogut.userId === userId
    }.subquery.map {
      case ((t, _), _) => (t, (Action.Manage: Rep[Action]).asColumnOf[Action])
    }
  }

  /** Returns group images scope with sorted by role members */
  def groupsScope = {
    table
      .join(PermissionsRepo.table)
      .on {
        case (t, pt) =>
          pt.imageId === t.id && pt.memberKind === (MemberKind.Group: MemberKind) &&
            t.ownerKind === (OwnerKind.Organization: OwnerKind)
      }
      .join(OrganizationGroupUsersRepo.table)
      .on { case ((_, pt), gut) => pt.memberId === gut.groupId }
      .sortBy {
        case ((_, pt), _) =>
          Case
            .If(pt.action === (Action.Manage: Action))
            .Then(1)
            .If(pt.action === (Action.Write: Action))
            .Then(2)
            .Else(3)
      }
  }

  private def availableByUserGroupsDBIO(userId: Rep[Int]) = {
    groupsScope.filter {
      case (_, gut) => gut.userId === userId
    }.subquery.map {
      case ((t, pt), _) => (t, pt.action)
    }
  }

  private def availableScope(userId: Rep[Int]) = {
    availableByUserOwnerDBIO(userId)
      .union(availableByUserPermissionsDBIO(userId))
      .union(availableByUserOrganizationsDBIO(userId))
      .union(availableByUserGroupsDBIO(userId))
      .subquery
  }

  private lazy val findIdByUserIdAndNameCompiled = Compiled {
    (userId: Rep[Int], slug: Rep[String]) =>
      availableScope(userId)
        .map(_._1)
        .filter(_.slug === slug.asColumnOfType[String]("citext"))
        .map(_.id)
  }

  private lazy val findRepositoriesByUserIdCompiled = Compiled {
    (userId: Rep[Int], limit: ConstColumn[Long], id: Rep[Int]) =>
      availableScope(userId).filter(_._1.id > id).sortBy(_._1.id.asc).map(_._1.slug).take(limit)
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
    val userId = user.getId()
    val owner  = Owner(userId, OwnerKind.User)
    create(
      Image(
        id = None,
        name = req.name,
        slug = s"${user.username}/${req.name}",
        owner = owner,
        visibilityKind = req.visibilityKind,
        description = req.description.getOrElse(""),
        createdByUserId = userId,
        createdAt = OffsetDateTime.now,
        updatedAt = None
      ))
  }

  def create(req: ImageCreateRequest, org: Organization, createdByUserId: Int)(
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
        createdByUserId = createdByUserId,
        createdAt = OffsetDateTime.now,
        updatedAt = None
      ))
  }

  override def createDBIO(item: Image)(implicit ec: ExecutionContext): ModelDBIO = {
    for {
      img <- super.createDBIO(item)
      imageId = img.getId()
      _ <- item.owner.kind match {
        case OwnerKind.User =>
          PermissionsRepo.createUserOwnerDBIO(imageId, img.owner.id, Action.Manage)
        case _ => DBIO.successful(())
      }
      _ <- EventsRepo.createRepoEventDBIO(
        repoId = imageId,
        name = img.name,
        slug = img.slug,
        createdByUserId = img.createdByUserId
      )
    } yield img
  }

  def update(id: Int, req: ImageUpdateRequest)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    update(id)(_.copy(description = req.description))
  }

  private lazy val findAvailableByUserIdCompiled = Compiled {
    (userId: Rep[Int], offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
      availableScope(userId).sortBy(_._1.slug).drop(offset).take(limit)
  }

  private lazy val findAvailableByUserIdTotalCompiled = Compiled { userId: Rep[Int] =>
    availableScope(userId).length
  }

  def paginateAvailableByUserId(userId: Int, p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[RepositoryResponse]] = {
    db.run {
      for {
        images <- findAvailableByUserIdCompiled((userId, p.offset, p.limit)).result
        total  <- findAvailableByUserIdTotalCompiled(userId).result
        data = images.map((ImageHelpers.responseFrom _).tupled)
      } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit)
    }
  }

  private def findPublicByOwnerDBIO(ownerId: Rep[Int], ownerKind: Rep[OwnerKind]) = {
    table.filter { q =>
      q.ownerId === ownerId && q.ownerKind === ownerKind &&
      q.visibilityKind === (ImageVisibilityKind.Public: ImageVisibilityKind)
    }.map(t => (t, (Action.Read: Rep[Action]).asColumnOf[Action])).subquery
  }

  private lazy val findPublicByOwnerCompiled = Compiled {
    (ownerId: Rep[Int], ownerKind: Rep[OwnerKind], offset: ConstColumn[Long],
     limit: ConstColumn[Long]) =>
      findPublicByOwnerDBIO(ownerId, ownerKind).drop(offset).take(limit)
  }

  private lazy val findPublicByOwnerTotalCompiled = Compiled {
    (ownerId: Rep[Int], ownerKind: Rep[OwnerKind]) =>
      findPublicByOwnerDBIO(ownerId, ownerKind).length
  }

  private def paginatePublicByOwnerDBIO(ownerId: Int, ownerKind: OwnerKind, p: Pagination)(
      implicit ec: ExecutionContext) = {
    for {
      images <- findPublicByOwnerCompiled((ownerId, ownerKind, p.offset, p.limit)).result
      total  <- findPublicByOwnerTotalCompiled((ownerId, ownerKind)).result
    } yield (images, total)
  }

  private def findAvailableByUserOwnerAsMemberDBIO(ownerId: Rep[Int], currentUserId: Rep[Int]) = {
    table
      .join(PermissionsRepo.table)
      .on(_.id === _.imageId)
      .filter {
        case (t, pt) =>
          t.ownerId === ownerId && t.ownerKind === (OwnerKind.User: OwnerKind) &&
            (t.ownerId === currentUserId ||
              (pt.memberId === currentUserId &&
                pt.memberKind === (MemberKind.User: MemberKind)))
      }
      .map { case (t, pt) => (t, pt.action) }
      .subquery
  }

  private def findAvailableByUserOwnerDBIO(ownerId: Rep[Int], currentUserId: Rep[Int]) = {
    findPublicByOwnerDBIO(ownerId, OwnerKind.User)
      .union(findAvailableByUserOwnerAsMemberDBIO(ownerId, currentUserId))
      .sortBy(_._1.name)
  }

  private lazy val findAvailableByUserOwnerCompiled = Compiled {
    (ownerId: Rep[Int], currentUserId: Rep[Int], offset: ConstColumn[Long],
     limit: ConstColumn[Long]) =>
      findAvailableByUserOwnerDBIO(ownerId, currentUserId).drop(offset).take(limit)
  }

  private lazy val findAvailableByUserOwnerTotalCompiled = Compiled {
    (ownerId: Rep[Int], currentUserId: Rep[Int]) =>
      findAvailableByUserOwnerDBIO(ownerId, currentUserId).length
  }

  def paginateAvailableByUserOwner(userId: Int, currentUserIdOpt: Option[Int], p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[RepositoryResponse]] = {
    val q = currentUserIdOpt match {
      case Some(id) =>
        for {
          images <- findAvailableByUserOwnerCompiled((userId, id, p.offset, p.limit)).result
          total  <- findAvailableByUserOwnerTotalCompiled((userId, id)).result
        } yield (images, total)
      case _ => paginatePublicByOwnerDBIO(userId, OwnerKind.User, p)
    }
    db.run(q.map {
      case (images, total) =>
        val data = images.map((ImageHelpers.responseFrom _).tupled)
        PaginationData(data, total = total, offset = p.offset, limit = p.limit)
    })
  }

  private lazy val findByUserOwnerCompiled = Compiled {
    (userId: Rep[Int], offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
      availableByUserOwnerDBIO(userId).sortBy(_._1.name).drop(offset).take(limit)
  }

  private lazy val findByUserOwnerTotalCompiled = Compiled { userId: Rep[Int] =>
    availableByUserOwnerDBIO(userId).length
  }

  def paginateByUser(userId: Int, p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[RepositoryResponse]] = {
    db.run(for {
      images <- findByUserOwnerCompiled((userId, p.offset, p.limit)).result
      total  <- findByUserOwnerTotalCompiled(userId).result
      data = images.map((ImageHelpers.responseFrom _).tupled)
    } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit))
  }

  private def availableByOrganizationAndUserDBIO(orgId: Rep[Int], userId: Rep[Int]) = {
    organizationAdminsScope.filter {
      case ((_, ogt), ogut) =>
        ogt.organizationId === orgId && ogut.userId === userId
    }.subquery.map {
      case ((t, _), _) => (t, Action.Manage: Action)
    }.subquery
  }

  private def availableByOrganizationAndUserGroupsDBIO(orgId: Rep[Int], userId: Rep[Int]) = {
    groupsScope.filter {
      case ((t, _), gut) => t.ownerId === orgId && gut.userId === userId
    }.subquery.map {
      case ((t, pt), _) => (t, pt.action)
    }.subquery
  }

  private def findAvailableByOrganizationOwnerDBIO(orgId: Rep[Int], currentUserId: Rep[Int]) = {
    findPublicByOwnerDBIO(orgId, OwnerKind.Organization)
      .union(availableByOrganizationAndUserDBIO(orgId, currentUserId))
      .union(availableByOrganizationAndUserGroupsDBIO(orgId, currentUserId))
      .sortBy(_._1.name)
  }

  private lazy val findAvailableByOrganizationOwnerCompiled = Compiled {
    (ownerId: Rep[Int], currentUserId: Rep[Int], offset: ConstColumn[Long],
     limit: ConstColumn[Long]) =>
      findAvailableByOrganizationOwnerDBIO(ownerId, currentUserId).drop(offset).take(limit)
  }

  private lazy val findAvailableByOrganizationOwnerTotalCompiled = Compiled {
    (ownerId: Rep[Int], currentUserId: Rep[Int]) =>
      findAvailableByOrganizationOwnerDBIO(ownerId, currentUserId).length
  }

  def paginateAvailableByOrganizationOwner(
      orgId: Int,
      currentUserIdOpt: Option[Int],
      p: Pagination)(implicit ec: ExecutionContext): Future[PaginationData[RepositoryResponse]] = {
    val q = currentUserIdOpt match {
      case Some(id) =>
        for {
          images <- findAvailableByOrganizationOwnerCompiled((orgId, id, p.offset, p.limit)).result
          total  <- findAvailableByOrganizationOwnerTotalCompiled((orgId, id)).result
        } yield (images, total)
      case _ => paginatePublicByOwnerDBIO(orgId, OwnerKind.Organization, p)
    }
    db.run(q.map {
      case (images, total) =>
        val data = images.map((ImageHelpers.responseFrom _).tupled)
        PaginationData(data, total = total, offset = p.offset, limit = p.limit)
    })
  }

  def updateVisibility(id: Int, visibilityKind: ImageVisibilityKind): Future[_] =
    db.run(table.filter(_.id === id).map(_.visibilityKind).update(visibilityKind))

  def destroyDBIO(id: Int)(implicit ec: ExecutionContext) = {
    for {
      _   <- ImageBlobsRepo.destroyByImageIdDBIO(id)
      res <- super.destroyDBIO(id)
    } yield res
  }

  def destroyByOrganizationIdDBIO(id: Int)(implicit ec: ExecutionContext) = {
    for {
      _ <- ImageBlobsRepo.destroyByOrganizationIdDBIO(id)
      res <- table.filter { t =>
        t.ownerId === id && t.ownerKind === (OwnerKind.Organization: OwnerKind)
      }.delete
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
