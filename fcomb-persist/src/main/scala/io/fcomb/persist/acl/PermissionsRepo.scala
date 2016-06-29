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

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.acl._
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.{PersistTableWithAutoIntPk, PersistModelWithAutoIntPk, OrganizationsRepo}
import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext

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

object PermissionsRepo extends PersistModelWithAutoIntPk[Permission, PermissionTable] {
  val table = TableQuery[PermissionTable]

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
    table += Permission(
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
}
