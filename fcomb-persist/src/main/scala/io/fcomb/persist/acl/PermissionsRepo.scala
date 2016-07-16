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

package io.fcomb.persist.acl

import cats.data.Validated
import cats.syntax.eq._
import io.fcomb.Db.db
import io.fcomb.FcombPostgresProfile.api._
import io.fcomb.models.acl._
import io.fcomb.models.docker.distribution.Image
import io.fcomb.models.{Pagination, PaginationData, OwnerKind, User}
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.{PaginationActions, PersistTableWithAutoIntPk, PersistModelWithAutoIntPk, OrganizationsRepo, OrganizationGroupsRepo, UsersRepo}
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.rpc.acl._
import io.fcomb.rpc.helpers.time.Implicits._
import io.fcomb.validations.{ValidationResult, ValidationResultUnit}
import java.time.ZonedDateTime
import scala.concurrent.{Future, ExecutionContext}
import slick.jdbc.TransactionIsolation

class PermissionTable(tag: Tag)
    extends Table[Permission](tag, "acl_permissions")
    with PersistTableWithAutoIntPk {
  def imageId    = column[Int]("image_id")
  def memberId   = column[Int]("member_id")
  def memberKind = column[MemberKind]("member_kind")
  def action     = column[Action]("action")
  def createdAt  = column[ZonedDateTime]("created_at")
  def updatedAt  = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, imageId, memberId, memberKind, action, createdAt, updatedAt) <>
      ((Permission.apply _).tupled, Permission.unapply)
}

object PermissionsRepo
    extends PersistModelWithAutoIntPk[Permission, PermissionTable]
    with PaginationActions {
  val table = TableQuery[PermissionTable]
  val label = "permissions"

  private def byImageAndMemberScopeDBIO(imageId: Rep[Int],
                                        memberId: Rep[Int],
                                        memberKind: Rep[MemberKind]) = {
    table.filter { q =>
      q.imageId === imageId && q.memberId === memberId && q.memberKind === memberKind
    }
  }

  private lazy val canReadByImageAndMemberCompiled = Compiled {
    (imageId: Rep[Int], memberId: Rep[Int], memberKind: Rep[MemberKind]) =>
      byImageAndMemberScopeDBIO(imageId, memberId, memberKind).exists
  }

  private lazy val canWriteByImageAndMemberCompiled = Compiled {
    (imageId: Rep[Int], memberId: Rep[Int], memberKind: Rep[MemberKind]) =>
      byImageAndMemberScopeDBIO(imageId, memberId, memberKind).filter { q =>
        q.action === (Action.Write: Action) || q.action === (Action.Manage: Action)
      }.exists
  }

  private lazy val canManageByImageAndMemberCompiled = Compiled {
    (imageId: Rep[Int], memberId: Rep[Int], memberKind: Rep[MemberKind]) =>
      byImageAndMemberScopeDBIO(imageId, memberId, memberKind).filter { q =>
        q.action === (Action.Manage: Action)
      }.exists
  }

  private def isAllowedActionByImageAndMemberDBIO(
      imageId: Int,
      memberId: Int,
      memberKind: MemberKind,
      action: Action): DBIOAction[Boolean, NoStream, Effect.Read] = {
    val args = (imageId, memberId, memberKind)
    val q = action match {
      case Action.Read   => canReadByImageAndMemberCompiled(args)
      case Action.Write  => canWriteByImageAndMemberCompiled(args)
      case Action.Manage => canManageByImageAndMemberCompiled(args)
    }
    q.result
  }

  def isAllowedActionByImageAsUserDBIO(
      imageId: Int,
      userId: Int,
      action: Action): DBIOAction[Boolean, NoStream, Effect.Read] = {
    isAllowedActionByImageAndMemberDBIO(imageId, userId, MemberKind.User, action)
  }

  private def byImageAndGroupMemberScopeDBIO(
      imageId: Rep[Int],
      organizationId: Rep[Int],
      userId: Rep[Int]
  ) =
    OrganizationsRepo.groupUsersScope.join(table).on {
      case (((_, gt), gut), pt) =>
        gt.organizationId === organizationId && gut.userId === userId && gt.id === pt.memberId &&
          pt.memberKind === (MemberKind.Group: MemberKind) && pt.imageId === imageId
    }

  private lazy val canReadByImageAndGroupMemberCompiled = Compiled {
    (imageId: Rep[Int], organizationId: Rep[Int], userId: Rep[Int]) =>
      byImageAndGroupMemberScopeDBIO(imageId, organizationId, userId).exists
  }

  private lazy val canWriteByImageAndGroupMemberCompiled = Compiled {
    (imageId: Rep[Int], organizationId: Rep[Int], userId: Rep[Int]) =>
      byImageAndGroupMemberScopeDBIO(imageId, organizationId, userId).filter {
        case (((_, _), _), pt) =>
          pt.action === (Action.Write: Action) || pt.action === (Action.Manage: Action)
      }.exists
  }

  private lazy val canManageByImageAndGroupMemberCompiled = Compiled {
    (imageId: Rep[Int], organizationId: Rep[Int], userId: Rep[Int]) =>
      byImageAndGroupMemberScopeDBIO(imageId, organizationId, userId).filter {
        case (((_, _), _), pt) => pt.action === (Action.Manage: Action)
      }.exists
  }

  private def isAllowedActionByImageAsGroupMemberDBIO(
      imageId: Int,
      organizationId: Int,
      userId: Int,
      action: Action): DBIOAction[Boolean, NoStream, Effect.Read] = {
    val args = (imageId, organizationId, userId)
    val q = action match {
      case Action.Read   => canReadByImageAndGroupMemberCompiled(args)
      case Action.Write  => canWriteByImageAndGroupMemberCompiled(args)
      case Action.Manage => canManageByImageAndGroupMemberCompiled(args)
    }
    q.result
  }

  def isAllowedActionByImageAsGroupUserDBIO(imageId: Int,
                                            organizationId: Int,
                                            userId: Int,
                                            action: Action)(
      implicit ec: ExecutionContext): DBIOAction[Boolean, NoStream, Effect.Read] = {
    isAllowedActionByImageAsUserDBIO(imageId, userId, action).flatMap { isAllowed =>
      if (isAllowed) DBIO.successful(true)
      else {
        OrganizationsRepo.isAdminDBIO(organizationId, userId).flatMap { isAdmin =>
          if (isAdmin) DBIO.successful(true)
          else
            isAllowedActionByImageAsGroupMemberDBIO(imageId, organizationId, userId, action)
        }
      }
    }
  }

  def createUserOwnerDBIO(imageId: Int, userId: Int, action: Action) = {
    tableWithPk += Permission(
      id = None,
      imageId = imageId,
      memberId = userId,
      memberKind = MemberKind.User,
      action = action,
      createdAt = ZonedDateTime.now(),
      updatedAt = None
    )
  }

  private type PermissionResponseTuple =
    (Int, MemberKind, Action, ZonedDateTime, Option[ZonedDateTime], (String, Option[String]))

  private type PermissionResponseTupleRep = (Rep[Int],
                                             Rep[MemberKind],
                                             Rep[Action],
                                             Rep[ZonedDateTime],
                                             Rep[Option[ZonedDateTime]],
                                             (Rep[String], Rep[Option[String]]))

  private def findByImageIdScopeDBIO(imageId: Rep[Int]) =
    table
      .join(UsersRepo.table)
      .on {
        case (t, ut) => t.memberKind === (MemberKind.User: MemberKind) && t.memberId === ut.id
      }
      .filter { case (t, ut) => t.imageId === imageId }
      .map {
        case (t, ut) =>
          (t.memberId,
           t.memberKind,
           t.action,
           t.createdAt,
           t.updatedAt,
           (ut.username, ut.fullName))
      }

  private def sortByPF(q: PermissionResponseTupleRep): PartialFunction[String, Rep[_]] = {
    case "member.id"       => q._1
    case "member.kind"     => q._2
    case "action"          => q._3
    case "createdAt"       => q._4
    case "updatedAt"       => q._5
    case "member.username" => q._6._1
  }

  private def findByImageIdAsReponseDBIO(imageId: Int, p: Pagination) = {
    val q = findByImageIdScopeDBIO(imageId).drop(p.offset).take(p.limit)
    sortByQuery(q, p)(sortByPF, _._5.asc)
  }

  private lazy val findByImageIdTotalCompiled = Compiled { (imageId: Rep[Int]) =>
    findByImageIdScopeDBIO(imageId).length
  }

  def findByImageIdWithPagination(image: Image, p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[PermissionResponse]] = {
    val imageId = image.getId()
    val f       = applyResponse(image)(_)
    db.run {
      for {
        permissions <- findByImageIdAsReponseDBIO(imageId, p).result
        total       <- findByImageIdTotalCompiled(imageId).result
      } yield PaginationData(permissions.map(f), total = total, offset = p.offset, limit = p.limit)
    }
  }

  private def applyResponse(image: Image)(t: PermissionResponseTuple): PermissionResponse =
    t match {
      case (userId, kind, action, createdAt, updatedAt, (username, fullName)) =>
        val isOwner = image.owner.kind === OwnerKind.User && image.owner.id == userId
        val member = PermissionUserMemberResponse(id = userId,
                                                  kind = MemberKind.User,
                                                  isOwner = isOwner,
                                                  name = username,
                                                  fullName = fullName)
        PermissionResponse(
          member = member,
          action = action,
          createdAt = createdAt.toIso8601,
          updatedAt = updatedAt.map(_.toIso8601)
        )
    }

  private def userIdByMemberRequestDBIO(req: PermissionMemberRequest)(
      implicit ec: ExecutionContext): DBIOAction[ValidationResult[User], NoStream, Effect.Read] = {
    req match {
      case PermissionUserIdRequest(id)     => UsersRepo.findByIdAsValidatedDBIO(id)
      case PermissionUsernameRequest(name) => UsersRepo.findByUsernameAsValidatedDBIO(name)
    }
  }

  private lazy val findIdByImageAndSourceCompiled = Compiled {
    (imageId: Rep[Int], memberId: Rep[Int]) =>
      table.filter { q =>
        q.imageId === imageId &&
        q.memberId === memberId &&
        q.memberKind === (MemberKind.User: MemberKind)
      }.take(1)
  }

  private lazy val cannotSetPermissionForOwner =
    validationError("owner", "Cannot set a permission for owner")

  def upsertByImage(image: Image, req: PermissionUserCreateRequest)(
      implicit ec: ExecutionContext): Future[ValidationResult[PermissionResponse]] = {
    runInTransaction(TransactionIsolation.Serializable) { // TODO: DRY
      userIdByMemberRequestDBIO(req.member).flatMap {
        case Validated.Valid(user) =>
          val memberId = user.getId
          if (memberId == image.owner.id && image.owner.kind === OwnerKind.User)
            DBIO.successful(cannotSetPermissionForOwner)
          else {
            findIdByImageAndSourceCompiled((image.getId(), memberId)).result.headOption.flatMap {
              case Some(p) =>
                val up = p.copy(
                  action = req.action,
                  updatedAt = Some(ZonedDateTime.now)
                )
                findByIdQuery(p.getId()).update(up).map(_ => up)
              case _ =>
                createUserOwnerDBIO(image.getId(), memberId, req.action)
            }.map { p =>
              val member = PermissionUserMemberResponse(
                id = memberId,
                kind = MemberKind.User,
                isOwner = false, // impossible to change the permissions for the owner
                name = user.username,
                fullName = user.fullName)
              Validated.Valid(
                PermissionResponse(
                  member = member,
                  action = p.action,
                  createdAt = p.createdAt.toIso8601,
                  updatedAt = p.updatedAt.map(_.toIso8601)
                ))
            }
          }
        case res @ Validated.Invalid(_) => DBIO.successful(res)
      }
    }
  }

  private def userIdBySlugDBIO(slug: String)(
      implicit ec: ExecutionContext): DBIOAction[ValidationResult[User], NoStream, Effect.Read] = {
    if (slug.forall(Character.isDigit)) UsersRepo.findByIdAsValidatedDBIO(slug.toInt)
    else UsersRepo.findByUsernameAsValidatedDBIO(slug)
  }

  def destroyByImage(image: Image, memberKind: MemberKind, slug: String)(
      implicit ec: ExecutionContext): Future[ValidationResultUnit] = {
    assert(memberKind === MemberKind.User) // TODO
    runInTransaction(TransactionIsolation.Serializable) { // TODO: DRY
      userIdBySlugDBIO(slug).flatMap {
        case Validated.Valid(user) =>
          val memberId = user.getId
          if (memberId == image.owner.id && image.owner.kind === OwnerKind.User)
            DBIO.successful(cannotSetPermissionForOwner)
          else {
            findIdByImageAndSourceCompiled((image.getId(), memberId)).result.headOption.flatMap {
              case Some(p) =>
                findByIdQuery(p.getId()).delete.map { res =>
                  if (res == 0) notFound
                  else Validated.Valid(())
                }
              case _ => DBIO.successful(notFound)
            }
          }
        case res @ Validated.Invalid(_) => DBIO.successful(res)
      }
    }
  }

  private lazy val notFound = validationError("permission", "Not found")

  def destroyByOrganizationIdDBIO(organizationId: Int)(implicit ec: ExecutionContext) = {
    table.filter { q =>
      q.imageId.in(ImagesRepo.findIdsByOrganizationIdDBIO(organizationId)) ||
      (q.memberId.in(OrganizationGroupsRepo.findIdsByOrganizationIdDBIO(organizationId)) &&
          q.memberKind === (MemberKind.Group: MemberKind))
    }.delete
  }

  def destroyByOrganizationGroupIdDBIO(groupId: Int)(implicit ec: ExecutionContext) = {
    table.filter { q =>
      q.memberId === groupId && q.memberKind === (MemberKind.Group: MemberKind)
    }.delete
  }
}
