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
import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.acl._
import io.fcomb.models.docker.distribution.Image
import io.fcomb.models.{Pagination, PaginationData, OwnerKind, User}
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.{PaginationActions, PersistTableWithAutoIntPk, PersistModelWithAutoIntPk, OrganizationsRepo, UsersRepo}
import io.fcomb.rpc.acl._
import io.fcomb.rpc.helpers.time.Implicits._
import io.fcomb.validations.{ValidationResult, ValidationResultUnit}
import java.time.ZonedDateTime
import scala.concurrent.{Future, ExecutionContext}
import slick.jdbc.TransactionIsolation

class PermissionTable(tag: Tag)
    extends Table[Permission](tag, "acl_permissions")
    with PersistTableWithAutoIntPk {
  def sourceId   = column[Int]("source_id")
  def sourceKind = column[SourceKind]("source_kind")
  def memberId   = column[Int]("member_id")
  def memberKind = column[MemberKind]("member_kind")
  def action     = column[Action]("action")
  def createdAt  = column[ZonedDateTime]("created_at")
  def updatedAt  = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, sourceId, sourceKind, memberId, memberKind, action, createdAt, updatedAt) <>
      ((Permission.apply _).tupled, Permission.unapply)
}

object PermissionsRepo
    extends PersistModelWithAutoIntPk[Permission, PermissionTable]
    with PaginationActions {
  val table = TableQuery[PermissionTable]
  val label = "permissions"

  private def bySourceAndMemberScopeDBIO(sourceId: Rep[Int],
                                         sourceKind: Rep[SourceKind],
                                         memberId: Rep[Int],
                                         memberKind: Rep[MemberKind]) =
    table.filter { q =>
      q.sourceId === sourceId && q.sourceKind === sourceKind && q.memberId === memberId &&
      q.memberKind === memberKind
    }

  private lazy val canReadBySourceAndMemberCompiled = Compiled {
    (sourceId: Rep[Int], sourceKind: Rep[SourceKind], memberId: Rep[Int],
     memberKind: Rep[MemberKind]) =>
      bySourceAndMemberScopeDBIO(sourceId, sourceKind, memberId, memberKind).exists
  }

  private lazy val canWriteBySourceAndMemberCompiled = Compiled {
    (sourceId: Rep[Int], sourceKind: Rep[SourceKind], memberId: Rep[Int],
     memberKind: Rep[MemberKind]) =>
      bySourceAndMemberScopeDBIO(sourceId, sourceKind, memberId, memberKind).filter { q =>
        q.action === (Action.Write: Action) || q.action === (Action.Manage: Action)
      }.exists
  }

  private lazy val canManageBySourceAndMemberCompiled = Compiled {
    (sourceId: Rep[Int], sourceKind: Rep[SourceKind], memberId: Rep[Int],
     memberKind: Rep[MemberKind]) =>
      bySourceAndMemberScopeDBIO(sourceId, sourceKind, memberId, memberKind).filter { q =>
        q.action === (Action.Manage: Action)
      }.exists
  }

  private def isAllowedActionBySourceAndMemberDBIO(
      sourceId: Int,
      sourceKind: SourceKind,
      memberId: Int,
      memberKind: MemberKind,
      action: Action): DBIOAction[Boolean, NoStream, Effect.Read] = {
    val args = (sourceId, sourceKind, memberId, memberKind)
    val q = action match {
      case Action.Read   => canReadBySourceAndMemberCompiled(args)
      case Action.Write  => canWriteBySourceAndMemberCompiled(args)
      case Action.Manage => canManageBySourceAndMemberCompiled(args)
    }
    q.result
  }

  def isAllowedActionBySourceAsUserDBIO(
      sourceId: Int,
      sourceKind: SourceKind,
      userId: Int,
      action: Action): DBIOAction[Boolean, NoStream, Effect.Read] = {
    isAllowedActionBySourceAndMemberDBIO(sourceId, sourceKind, userId, MemberKind.User, action)
  }

  private def bySourceAndGroupMemberScopeDBIO(
      sourceId: Rep[Int],
      sourceKind: Rep[SourceKind],
      organizationId: Rep[Int],
      userId: Rep[Int]
  ) =
    OrganizationsRepo.groupUsersScope.join(table).on {
      case (((_, gt), gut), pt) =>
        gt.organizationId === organizationId && gut.userId === userId && gt.id === pt.memberId &&
          pt.memberKind === (MemberKind.Group: MemberKind) && pt.sourceId === sourceId
    }

  private lazy val canReadBySourceAndGroupMemberCompiled = Compiled {
    (sourceId: Rep[Int], sourceKind: Rep[SourceKind], organizationId: Rep[Int],
     userId: Rep[Int]) =>
      bySourceAndGroupMemberScopeDBIO(sourceId, sourceKind, organizationId, userId).exists
  }

  private lazy val canWriteBySourceAndGroupMemberCompiled = Compiled {
    (sourceId: Rep[Int], sourceKind: Rep[SourceKind], organizationId: Rep[Int],
     userId: Rep[Int]) =>
      bySourceAndGroupMemberScopeDBIO(sourceId, sourceKind, organizationId, userId).filter {
        case (((_, _), _), pt) =>
          pt.action === (Action.Write: Action) || pt.action === (Action.Manage: Action)
      }.exists
  }

  private lazy val canManageBySourceAndGroupMemberCompiled = Compiled {
    (sourceId: Rep[Int], sourceKind: Rep[SourceKind], organizationId: Rep[Int],
     userId: Rep[Int]) =>
      bySourceAndGroupMemberScopeDBIO(sourceId, sourceKind, organizationId, userId).filter {
        case (((_, _), _), pt) => pt.action === (Action.Manage: Action)
      }.exists
  }

  private def isAllowedActionBySourceAsGroupMemberDBIO(
      sourceId: Int,
      sourceKind: SourceKind,
      organizationId: Int,
      userId: Int,
      action: Action): DBIOAction[Boolean, NoStream, Effect.Read] = {
    val args = (sourceId, sourceKind, organizationId, userId)
    val q = action match {
      case Action.Read   => canReadBySourceAndGroupMemberCompiled(args)
      case Action.Write  => canWriteBySourceAndGroupMemberCompiled(args)
      case Action.Manage => canManageBySourceAndGroupMemberCompiled(args)
    }
    q.result
  }

  def isAllowedActionBySourceAsGroupUserDBIO(sourceId: Int,
                                             sourceKind: SourceKind,
                                             organizationId: Int,
                                             userId: Int,
                                             action: Action)(
      implicit ec: ExecutionContext): DBIOAction[Boolean, NoStream, Effect.Read] = {
    isAllowedActionBySourceAsUserDBIO(sourceId, sourceKind, userId, action).flatMap {
      case false =>
        OrganizationsRepo.isAdminDBIO(userId).flatMap {
          case false =>
            isAllowedActionBySourceAsGroupMemberDBIO(sourceId,
                                                     sourceKind,
                                                     organizationId,
                                                     userId,
                                                     action)
          case res => DBIO.successful(res)
        }
      case res => DBIO.successful(res)
    }
  }

  def createUserOwnerDBIO(sourceId: Int, sourceKind: SourceKind, userId: Int, action: Action) = {
    tableWithPk += Permission(
      id = None,
      sourceId = sourceId,
      sourceKind = sourceKind,
      memberId = userId,
      memberKind = MemberKind.User,
      action = action,
      createdAt = ZonedDateTime.now(),
      updatedAt = None
    )
  }

  private type PermissionResponseTuple = (Int,
                                          MemberKind,
                                          Action,
                                          ZonedDateTime,
                                          Option[ZonedDateTime],
                                          (Option[String], Option[String]))

  private type PermissionResponseTupleRep = (Rep[Int],
                                             Rep[MemberKind],
                                             Rep[Action],
                                             Rep[ZonedDateTime],
                                             Rep[Option[ZonedDateTime]],
                                             (Rep[Option[String]], Rep[Option[String]]))

  private def findByImageIdScopeDBIO(imageId: Rep[Int]) =
    table
      .joinLeft(UsersRepo.table)
      .on {
        case (t, ut) => t.memberKind === (MemberKind.User: MemberKind) && t.memberId === ut.id
      }
      .filter {
        case (t, ut) =>
          t.sourceId === imageId && t.sourceKind === (SourceKind.DockerDistributionImage: SourceKind)
      }
      .map {
        case (t, ut) =>
          (t.memberId,
           t.memberKind,
           t.action,
           t.createdAt,
           t.updatedAt,
           (ut.map(_.username), ut.flatMap(_.fullName)))
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
                                                  username = username,
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
        q.sourceId === imageId &&
        q.sourceKind === (SourceKind.DockerDistributionImage: SourceKind) &&
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
                createUserOwnerDBIO(image.getId(),
                                    SourceKind.DockerDistributionImage,
                                    memberId,
                                    req.action)
            }.map { p =>
              val member = PermissionUserMemberResponse(
                id = memberId,
                kind = MemberKind.User,
                isOwner = false, // impossible to change the permissions for the owner
                username = Some(user.username),
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
}
