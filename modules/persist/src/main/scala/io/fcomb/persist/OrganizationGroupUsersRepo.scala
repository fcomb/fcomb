/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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
import io.fcomb.models.common.Slug
import io.fcomb.models.{OrganizationGroupUser, Pagination, PaginationData}
import io.fcomb.persist.PaginationActions._
import io.fcomb.PostgresProfile.api._
import io.fcomb.rpc.{MemberUserRequest, UserProfileResponse}
import scala.concurrent.ExecutionContext
import slick.jdbc.TransactionIsolation

final class OrganizationGroupUserTable(tag: Tag)
    extends Table[OrganizationGroupUser](tag, "organization_group_users") {
  def groupId = column[Int]("group_id", O.PrimaryKey)
  def userId  = column[Int]("user_id", O.PrimaryKey)

  def * = (groupId, userId).mapTo[OrganizationGroupUser]
}

object OrganizationGroupUsersRepo
    extends PersistModel[OrganizationGroupUser, OrganizationGroupUserTable] {
  val table = TableQuery[OrganizationGroupUserTable]
  val label = "members"

  private lazy val findSuggestionsUsersC = Compiled {
    (groupId: Rep[Int], q: Rep[String], limit: ConstColumn[Long]) =>
      val query = q.asColumnOfType[String]("citext")
      UsersRepo.table
        .filter { t =>
          (t.username.like(query) || t.fullName.like(query)) &&
          !t.id.in(table.filter(_.groupId === groupId).map(_.userId))
        }
        .map(t => (t.id, t.username, t.fullName))
        .sortBy(_._2)
        .take(limit)
  }

  def findSuggestionsUsers(groupId: Int, q: String, limit: Long = 16L)(
      implicit ec: ExecutionContext): DBIO[Seq[UserProfileResponse]] =
    findSuggestionsUsersC((groupId, s"$q%".trim, limit)).result
      .map(_.map(UserProfileResponse.tupled))

  type UserProfileResponseTupleRep = (Rep[Int], Rep[String], Rep[Option[String]])

  private def findByGroupIdScopeDBIO(groupId: Rep[Int]) =
    table.join(UsersRepo.table).on(_.userId === _.id).filter(_._1.groupId === groupId).map {
      case (_, u) => (u.id, u.username, u.fullName)
    }

  private lazy val findByGroupIdTotalC = Compiled { groupId: Rep[Int] =>
    findByGroupIdScopeDBIO(groupId).length
  }

  private def sortByPF(q: UserProfileResponseTupleRep): PartialFunction[String, Rep[_]] = {
    case "id"       => q._1
    case "username" => q._2
    case "fullName" => q._3
  }

  def paginateByGroupId(groupId: Int, p: Pagination)(
      implicit ec: ExecutionContext): DBIO[PaginationData[UserProfileResponse]] =
    for {
      members <- sortPaginate(findByGroupIdScopeDBIO(groupId), p)(sortByPF, _._2).result
      total   <- findByGroupIdTotalC(groupId).result
      data = members.map(UserProfileResponse.tupled)
    } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit)

  def createDBIO(groupId: Int, userId: Int) =
    table += OrganizationGroupUser(groupId = groupId, userId = userId)

  private lazy val existByGroupAndUserIdC = Compiled { (groupId: Rep[Int], userId: Rep[Int]) =>
    table.filter { t =>
      t.groupId === groupId && t.userId === userId
    }.exists
  }

  def upsertDBIO(groupId: Int, req: MemberUserRequest)(implicit ec: ExecutionContext) =
    UsersRepo.findByMemberRequestAsValidatedDBIO(req).flatMap {
      case Validated.Valid(user) =>
        val userId = user.getId()
        existByGroupAndUserIdC((groupId, userId)).result.flatMap { exist =>
          if (exist) DBIO.successful(Validated.Valid(()))
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

  def upsert(groupId: Int, req: MemberUserRequest)(implicit ec: ExecutionContext) =
    upsertDBIO(groupId, req)

  private def destroyDBIO(groupId: Int, userId: Int) =
    table.filter { t =>
      t.groupId === groupId && t.userId === userId
    }.delete

  private def destroyAsValidatedDBIO(groupId: Int, userId: Int)(implicit ec: ExecutionContext) =
    destroyDBIO(groupId, userId).map { res =>
      if (res == 0) notFound
      else Validated.Valid(())
    }

  def destroy(groupId: Int, userId: Int, memberSlug: Slug)(implicit ec: ExecutionContext) =
    runInTransaction(TransactionIsolation.Serializable) {
      UsersRepo.findAsValidatedDBIO(memberSlug).flatMap {
        case Validated.Valid(member) =>
          def destroy() = destroyAsValidatedDBIO(groupId, member.getId())

          if (member.getId() == userId) {
            OrganizationGroupsRepo.existsAdminGroupApartFromDBIO(groupId, userId).flatMap {
              case true => destroy()
              case _    => cannotDeleteAdminGroupMember
            }
          } else destroy()
        case res @ Validated.Invalid(_) => DBIO.successful(res)
      }
    }

  private lazy val cannotDeleteAdminGroupMember =
    validationErrorDBIO("user", "Cannot remove yourself from the last own admin group")

  private lazy val notFound = validationError("member", "Not found")
}
