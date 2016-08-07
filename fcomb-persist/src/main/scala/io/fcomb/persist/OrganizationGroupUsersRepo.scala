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

package io.fcomb.persist

import cats.data.Validated
import io.fcomb.Db.db
import io.fcomb.FcombPostgresProfile.api._
import io.fcomb.models.{Pagination, PaginationData, OrganizationGroupUser}
import io.fcomb.rpc.{MemberUserRequest, UserProfileResponse}
import scala.concurrent.{Future, ExecutionContext}

class OrganizationGroupUserTable(tag: Tag)
    extends Table[OrganizationGroupUser](tag, "organization_group_users") {
  def groupId = column[Int]("group_id", O.PrimaryKey)
  def userId  = column[Int]("user_id", O.PrimaryKey)

  def * =
    (groupId, userId) <>
      (OrganizationGroupUser.tupled, OrganizationGroupUser.unapply)
}

object OrganizationGroupUsersRepo
    extends PersistModel[OrganizationGroupUser, OrganizationGroupUserTable] {
  val table = TableQuery[OrganizationGroupUserTable]
  val label = "members"

  private def findByGroupIdScopeDBIO(groupId: Rep[Int]) = {
    table.join(UsersRepo.table).on(_.userId === _.id).filter(_._1.groupId === groupId).map {
      case (_, u) => (u.pk, u.email, u.username, u.fullName)
    }
  }

  private lazy val findByGroupIdCompiled = Compiled {
    (groupId: Rep[Int], offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
      findByGroupIdScopeDBIO(groupId).sortBy(_._3).drop(offset).take(limit)
  }

  private lazy val findByGroupIdTotalCompiled = Compiled { groupId: Rep[Int] =>
    findByGroupIdScopeDBIO(groupId).length
  }

  def paginateByGroupId(groupId: Int, p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[UserProfileResponse]] = {
    db.run {
      for {
        members <- findByGroupIdCompiled((groupId, p.offset, p.limit)).result
        total   <- findByGroupIdTotalCompiled(groupId).result
        data = members.map(UserProfileResponse.tupled)
      } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit)
    }
  }

  def createDBIO(groupId: Int, userId: Int) = {
    table += OrganizationGroupUser(
      groupId = groupId,
      userId = userId
    )
  }

  private lazy val existByGroupAndUserIdCompiled = Compiled {
    (groupId: Rep[Int], userId: Rep[Int]) =>
      table.filter { t =>
        t.groupId === groupId && t.userId === userId
      }.exists
  }

  def upsertDBIO(groupId: Int, req: MemberUserRequest)(implicit ec: ExecutionContext) = {
    UsersRepo.findByMemberRequestAsValidatedDBIO(req).flatMap {
      case Validated.Valid(user) =>
        val userId = user.getId()
        existByGroupAndUserIdCompiled((groupId, userId)).result.flatMap { exist =>
          if (exist) DBIO.successful(())
          else {
            val member = OrganizationGroupUser(
              groupId = groupId,
              userId = userId
            )
            (table += member).map(_ => Validated.Valid(()))
          }
        }
      case res @ Validated.Invalid(_) => DBIO.successful(res)
    }
  }

  def upsert(groupId: Int, req: MemberUserRequest)(implicit ec: ExecutionContext) = {
    db.run(upsertDBIO(groupId, req))
  }

  def destroyDBIO(groupId: Int, userId: Int) = {
    table.filter { t =>
      t.groupId === groupId && t.userId === userId
    }.delete
  }

  def destroy(groupId: Int, userId: Int) = {
    db.run(destroyDBIO(groupId: Int, userId))
  }
}
