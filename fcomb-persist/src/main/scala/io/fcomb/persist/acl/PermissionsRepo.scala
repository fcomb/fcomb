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
import io.fcomb.PostgresProfile.api._
import io.fcomb.models.OrganizationGroup
import io.fcomb.models.acl._
import io.fcomb.models.common.Slug
import io.fcomb.models.docker.distribution.Image
import io.fcomb.models.{Pagination, PaginationData, OwnerKind, User}
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.persist.{
  PaginationActions,
  PersistTableWithAutoIntPk,
  PersistModelWithAutoIntPk,
  OrganizationsRepo,
  OrganizationGroupsRepo,
  UsersRepo
}
import io.fcomb.rpc.acl._
import io.fcomb.validation.{ValidationResult, ValidationResultUnit}
import java.time.OffsetDateTime
import scala.concurrent.{Future, ExecutionContext}
import slick.jdbc.TransactionIsolation

class PermissionTable(tag: Tag)
    extends Table[Permission](tag, "acl_permissions")
    with PersistTableWithAutoIntPk {
  def imageId    = column[Int]("image_id")
  def memberId   = column[Int]("member_id")
  def memberKind = column[MemberKind]("member_kind")
  def action     = column[Action]("action")
  def createdAt  = column[OffsetDateTime]("created_at")
  def updatedAt  = column[Option[OffsetDateTime]]("updated_at")

  def * =
    (id.?, imageId, memberId, memberKind, action, createdAt, updatedAt) <>
      ((Permission.apply _).tupled, Permission.unapply)
}

object PermissionsRepo
    extends PersistModelWithAutoIntPk[Permission, PermissionTable]
    with PaginationActions {
  val table = TableQuery[PermissionTable]
  val label = "permissions"

  private def findIdByImageAndMemberScope(imageId: Rep[Int],
                                          userId: Rep[Int],
                                          memberKind: Rep[MemberKind]) = {
    table.filter { q =>
      q.imageId === imageId &&
      q.memberId === userId &&
      q.memberKind === memberKind
    }.take(1)
  }

  private lazy val findIdByImageAndMemberCompiled = Compiled {
    (imageId: Rep[Int], memberId: Rep[Int], memberKind: Rep[MemberKind]) =>
      findIdByImageAndMemberScope(imageId, memberId, memberKind)
  }

  private lazy val findIdByImageAndUserCompiled = Compiled {
    (imageId: Rep[Int], userId: Rep[Int]) =>
      findIdByImageAndMemberScope(imageId, userId, MemberKind.User)
  }

  private def findIdByImageAndUserDBIO(imageId: Rep[Int], userId: Rep[Int]) = {
    findIdByImageAndMemberScope(imageId, userId, MemberKind.User).map(_.action).subquery
  }

  private lazy val findActionByImageAndUserCompiled = Compiled {
    (imageId: Rep[Int], userId: Rep[Int]) =>
      findIdByImageAndUserDBIO(imageId, userId)
  }

  private lazy val findIdByImageAndGroupCompiled = Compiled {
    (imageId: Rep[Int], userId: Rep[Int]) =>
      findIdByImageAndMemberScope(imageId, userId, MemberKind.Group)
  }

  def findActionByImageAndUserDBIO(
      imageId: Int,
      userId: Int): DBIOAction[Option[Action], NoStream, Effect.Read] = {
    findActionByImageAndUserCompiled((imageId, userId)).result.headOption
  }

  private def findActionByUserOrganizationsDBIO(imageId: Rep[Int],
                                                organizationId: Rep[Int],
                                                userId: Rep[Int]) = {
    ImagesRepo.organizationAdminsScope.filter {
      case ((t, _), ogut) =>
        t.id === imageId && t.ownerId === organizationId &&
          ogut.userId === userId
    }.take(1).map(_ => (Action.Manage: Rep[Action]).asColumnOf[Action]).subquery
  }

  private def findActionByUserGroupsDBIO(imageId: Rep[Int],
                                         organizationId: Rep[Int],
                                         userId: Rep[Int]) = {
    ImagesRepo.groupsScope.filter {
      case ((t, _), gut) =>
        t.id === imageId && t.ownerId === organizationId &&
          gut.userId === userId
    }.take(1).map(_._1._2.action).subquery
  }

  private lazy val findActionByImageAsGroupUserCompiled = Compiled {
    (imageId: Rep[Int], organizationId: Rep[Int], userId: Rep[Int]) =>
      findIdByImageAndUserDBIO(imageId, userId)
        .union(findActionByUserOrganizationsDBIO(imageId, organizationId, userId))
        .union(findActionByUserGroupsDBIO(imageId, organizationId, userId))
  }

  def findActionByImageAsGroupUserDBIO(imageId: Int, organizationId: Int, userId: Int)(
      implicit ec: ExecutionContext): DBIOAction[Option[Action], NoStream, Effect.Read] = {
    findActionByImageAsGroupUserCompiled((imageId, organizationId, userId)).result.headOption
  }

  def createMemberOwnerDBIO(imageId: Int, memberId: Int, memberKind: MemberKind, action: Action) = {
    tableWithPk += Permission(
      id = None,
      imageId = imageId,
      memberId = memberId,
      memberKind = memberKind,
      action = action,
      createdAt = OffsetDateTime.now(),
      updatedAt = None
    )
  }

  def createUserOwnerDBIO(imageId: Int, userId: Int, action: Action) =
    createMemberOwnerDBIO(imageId, userId, MemberKind.User, action)

  private def findMemberIdsByImageIdAndMemberKindDBIO(imageId: Rep[Int], memberKind: MemberKind) = {
    table.filter { t =>
      t.imageId === imageId && t.memberKind === memberKind
    }.map(_.memberId)
  }

  def findUserMemberIdsByImageIdDBIO(imageId: Rep[Int]) =
    findMemberIdsByImageIdAndMemberKindDBIO(imageId, MemberKind.User)

  def findGroupMemberIdsByImageIdDBIO(imageId: Rep[Int]) =
    findMemberIdsByImageIdAndMemberKindDBIO(imageId, MemberKind.Group)

  private type PermissionResponseTuple =
    (Int, MemberKind, Action, OffsetDateTime, Option[OffsetDateTime], (String, Option[String]))

  private type PermissionResponseTupleRep = (Rep[Int],
                                             Rep[MemberKind],
                                             Rep[Action],
                                             Rep[OffsetDateTime],
                                             Rep[Option[OffsetDateTime]],
                                             (Rep[String], Rep[Option[String]]))

  private def findByImageIdScopeDBIO(imageId: Rep[Int]) = {
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

  def paginateByImageId(image: Image, p: Pagination)(
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
                                                  isOwner = isOwner,
                                                  username = username,
                                                  fullName = fullName)
        PermissionResponse(
          member = member,
          action = action,
          createdAt = createdAt.toString,
          updatedAt = updatedAt.map(_.toString)
        )
    }

  private def userIdByMemberRequestDBIO(req: PermissionUserRequest)(
      implicit ec: ExecutionContext): DBIOAction[ValidationResult[User], NoStream, Effect.Read] = {
    req match {
      case PermissionUserIdRequest(id)     => UsersRepo.findByIdAsValidatedDBIO(id)
      case PermissionUsernameRequest(name) => UsersRepo.findByUsernameAsValidatedDBIO(name)
    }
  }

  private def groupIdByMemberRequestDBIO(orgId: Int, req: PermissionGroupRequest)(
      implicit ec: ExecutionContext)
    : DBIOAction[ValidationResult[OrganizationGroup], NoStream, Effect.Read] = {
    req match {
      case PermissionGroupIdRequest(id) =>
        OrganizationGroupsRepo.findByIdAsValidatedDBIO(orgId, id)
      case PermissionGroupNameRequest(name) =>
        OrganizationGroupsRepo.findByNameAsValidatedDBIO(orgId, name)
    }
  }

  private lazy val cannotSetPermissionForOwner =
    validationError("owner", "Cannot set a permission for owner")

  def upsertByImage(image: Image, req: PermissionCreateRequest)(
      implicit ec: ExecutionContext): Future[ValidationResult[PermissionResponse]] = {
    runInTransaction(TransactionIsolation.Serializable) { // TODO: DRY
      req.member match {
        case member: PermissionUserRequest  => upsertUser(image, member, req.action)
        case member: PermissionGroupRequest => upsertGroup(image, member, req.action)
      }
    }
  }

  private def upsertUser(image: Image, member: PermissionUserRequest, action: Action)(
      implicit ec: ExecutionContext) = {
    userIdByMemberRequestDBIO(member).flatMap {
      case Validated.Valid(user) =>
        val memberId = user.getId
        if (memberId == image.owner.id && image.owner.kind === OwnerKind.User)
          DBIO.successful(cannotSetPermissionForOwner)
        else {
          findIdByImageAndUserCompiled((image.getId(), memberId)).result.headOption.flatMap {
            case Some(p) =>
              val up = p.copy(
                action = action,
                updatedAt = Some(OffsetDateTime.now)
              )
              findByIdQuery(p.getId()).update(up).map(_ => up)
            case _ =>
              createUserOwnerDBIO(image.getId(), memberId, action)
          }.map { p =>
            val member = PermissionUserMemberResponse(
              id = memberId,
              isOwner = false, // impossible to change the permissions for the owner
              username = user.username,
              fullName = user.fullName)
            Validated.Valid(
              PermissionResponse(
                member = member,
                action = p.action,
                createdAt = p.createdAt.toString,
                updatedAt = p.updatedAt.map(_.toString)
              ))
          }
        }
      case res @ Validated.Invalid(_) => DBIO.successful(res)
    }
  }

  private def upsertGroup(image: Image, member: PermissionGroupRequest, action: Action)(
      implicit ec: ExecutionContext) = {
    if (image.owner.kind == OwnerKind.Organization) {
      groupIdByMemberRequestDBIO(image.owner.id, member).flatMap {
        case Validated.Valid(group) =>
          val memberId = group.getId
          findIdByImageAndGroupCompiled((image.getId(), memberId)).result.headOption.flatMap {
            case Some(p) =>
              val up = p.copy(
                action = action,
                updatedAt = Some(OffsetDateTime.now)
              )
              findByIdQuery(p.getId()).update(up).map(_ => up)
            case _ =>
              createMemberOwnerDBIO(image.getId(), memberId, MemberKind.Group, action)
          }.map { p =>
            val member = PermissionGroupMemberResponse(id = memberId, name = group.name)
            Validated.Valid(
              PermissionResponse(
                member = member,
                action = p.action,
                createdAt = p.createdAt.toString,
                updatedAt = p.updatedAt.map(_.toString)
              ))
          }
        case res @ Validated.Invalid(_) => DBIO.successful(res)
      }
    } else validationErrorAsDBIO("owner.kind", "Should be organization")
  }

  def findSuggestions(image: Image, q: String)(
      implicit ec: ExecutionContext): Future[Seq[PermissionMemberResponse]] = {
    image.owner.kind match {
      case OwnerKind.User =>
        UsersRepo.findSuggestions(image.getId(), image.owner.id, q)
      case OwnerKind.Organization =>
        OrganizationsRepo.findSuggestions(image.getId(), image.owner.id, q)
    }
  }

  def destroyByImage(image: Image, memberKind: MemberKind, slug: Slug)(
      implicit ec: ExecutionContext): Future[ValidationResultUnit] = {
    runInTransaction(TransactionIsolation.Serializable) {
      memberKind match {
        case MemberKind.User  => destroyByUser(image, slug)
        case MemberKind.Group => destroyByGroup(image, slug)
      }
    }
  }

  private def destroyByMemberAsValidated(imageId: Int, memberId: Int, memberKind: MemberKind)(
      implicit ec: ExecutionContext) = {
    findIdByImageAndMemberCompiled((imageId, memberId, memberKind)).result.headOption.flatMap {
      case Some(p) =>
        findByIdQuery(p.getId()).delete.map { res =>
          if (res == 0) notFound
          else Validated.Valid(())
        }
      case _ => DBIO.successful(notFound)
    }
  }

  private def destroyByUser(image: Image, slug: Slug)(implicit ec: ExecutionContext) = {
    UsersRepo.findBySlugAsValidatedDBIO(slug).flatMap {
      case Validated.Valid(user) =>
        val memberId = user.getId
        if (memberId == image.owner.id && image.owner.kind === OwnerKind.User)
          DBIO.successful(cannotSetPermissionForOwner)
        else destroyByMemberAsValidated(image.getId(), memberId, MemberKind.User)
      case res @ Validated.Invalid(_) => DBIO.successful(res)
    }
  }

  private def destroyByGroup(image: Image, slug: Slug)(implicit ec: ExecutionContext) = {
    if (image.owner.kind == OwnerKind.Organization) {
      OrganizationGroupsRepo.findBySlugAsValidatedDBIO(image.owner.id, slug).flatMap {
        case Validated.Valid(group) =>
          destroyByMemberAsValidated(image.getId(), group.getId(), MemberKind.Group)
        case res @ Validated.Invalid(_) => DBIO.successful(res)
      }
    } else validationErrorAsDBIO("owner.kind", "Should be organization")
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
