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

import io.fcomb.Db.db
import io.fcomb.PostgresProfile.api._
import io.fcomb.models.acl.{MemberKind, Role}
import io.fcomb.models.common.Slug
import io.fcomb.models.{Organization, Pagination, PaginationData}
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.Filters._
import io.fcomb.persist.PaginationActions._
import io.fcomb.rpc.acl.{
  PermissionGroupMemberResponse,
  PermissionMemberResponse,
  PermissionUserMemberResponse
}
import io.fcomb.rpc.helpers.OrganizationHelpers
import io.fcomb.rpc.{OrganizationCreateRequest, OrganizationResponse}
import io.fcomb.validation._
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

final class OrganizationTable(tag: Tag)
    extends Table[Organization](tag, "organizations")
    with PersistTableWithAutoIntPk {
  def name        = column[String]("name")
  def ownerUserId = column[Int]("owner_user_id")
  def createdAt   = column[OffsetDateTime]("created_at")
  def updatedAt   = column[Option[OffsetDateTime]]("updated_at")

  def * =
    (id.?, name, ownerUserId, createdAt, updatedAt) <>
      ((Organization.apply _).tupled, Organization.unapply)
}

object OrganizationsRepo extends PersistModelWithAutoIntPk[Organization, OrganizationTable] {
  val table = TableQuery[OrganizationTable]
  val label = "organizations"

  lazy val findByNameCompiled = Compiled { name: Rep[String] =>
    table.filter(_.name === name.asColumnOfType[String]("citext")).take(1)
  }

  def findBySlugDBIO(slug: Slug) =
    slug match {
      case Slug.Id(id)     => findByIdCompiled(id)
      case Slug.Name(name) => findByNameCompiled(name)
    }

  def findBySlug(slug: Slug)(implicit ec: ExecutionContext): Future[Option[Organization]] =
    db.run(findBySlugDBIO(slug).result.headOption)

  def groupsScope =
    table
      .join(OrganizationGroupsRepo.table)
      .on(_.id === _.organizationId)
      .join(OrganizationGroupUsersRepo.table)
      .on(_._2.id === _.groupId)
      .sortBy {
        case ((_, ogt), _) =>
          Case
            .If(ogt.role === (Role.Admin: Role))
            .Then(1)
            .If(ogt.role === (Role.Creator: Role))
            .Then(2)
            .Else(3)
      }

  private def availableByUserOwnerDBIO(userId: Rep[Int]) =
    table.filter(_.ownerUserId === userId).map(t => (t, (Role.Admin: Rep[Role]).asColumnOf[Role]))

  private def availableByUserGroupsDBIO(userId: Rep[Int]) =
    groupsScope
      .filter {
        case (_, ogut) => ogut.userId === userId
      }
      .map { case ((t, ogt), _) => (t, ogt.role) }
      .subquery

  private type AvailableScopeQuery =
    Query[(OrganizationTable, Rep[Role]), (Organization, Role), Seq]

  private def availableByUserIdScopeDBIO(userId: Rep[Int]): AvailableScopeQuery =
    availableByUserOwnerDBIO(userId).union(availableByUserGroupsDBIO(userId)).subquery

  private def availableByUserIdScopeDBIO(userId: Rep[Int], p: Pagination): AvailableScopeQuery =
    filter(availableByUserIdScopeDBIO(userId), p) {
      case ((t, role), value) => {
        case "name" => filterCitextByMask(t.name, value)
        case "role" => filterByEnum(role, Role, value)
      }
    }.subquery

  type OrganizationResponseTupleRep = (OrganizationTable, Rep[Role])

  private def sortByPF(q: OrganizationResponseTupleRep): PartialFunction[String, Rep[_]] = {
    case "id"   => q._1.id
    case "name" => q._1.name
  }

  def paginateAvailableByUserId(userId: Int, p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[OrganizationResponse]] = {
    val scope = availableByUserIdScopeDBIO(userId, p)
    db.run(for {
      orgs  <- sortPaginate(scope, p)(sortByPF, _._1.name).result
      total <- scope.length.result
      data = orgs.map { case (org, role) => OrganizationHelpers.response(org, role) }
    } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit))
  }

  private lazy val findRoleByIdAndUserIdCompiled = Compiled { (id: Rep[Int], userId: Rep[Int]) =>
    groupsScope
      .filter {
        case ((t, _), ogut) => t.id === id && ogut.userId === userId
      }
      .map(_._1._2.role)
      .take(1)
  }

  def findWithRoleBySlugDBIO(slug: Slug, userId: Int)(implicit ec: ExecutionContext) =
    for {
      orgOpt <- findBySlugDBIO(slug).result.headOption
      res <- orgOpt match {
        case Some(org) =>
          findRoleByIdAndUserIdCompiled((org.getId(), userId)).result.headOption.map(r =>
            Some((org, r)))
        case _ => DBIO.successful(None)
      }
    } yield res

  def findWithRoleBySlug(slug: Slug, userId: Int)(implicit ec: ExecutionContext) =
    db.run(findWithRoleBySlugDBIO(slug, userId))

  def findBySlugWithAclDBIO(slug: Slug, userId: Int, role: Role)(implicit ec: ExecutionContext) =
    findWithRoleBySlugDBIO(slug, userId).map {
      case Some((org, Some(userRole))) if userRole.has(role) => Some(org)
      case _                                                 => None
    }

  def findBySlugWithAcl(slug: Slug, userId: Int, role: Role)(
      implicit ec: ExecutionContext): Future[Option[Organization]] =
    db.run(findBySlugWithAclDBIO(slug, userId, role))

  private lazy val isAdminCompiled = Compiled { (id: Rep[Int], userId: Rep[Int]) =>
    groupsScope.filter {
      case ((t, ogt), ogut) =>
        t.id === id && ogut.userId === userId && ogt.role === (Role.Admin: Role)
    }.exists
  }

  def isAdminDBIO(id: Int, userId: Int) =
    isAdminCompiled((id, userId)).result

  def create(req: OrganizationCreateRequest, userId: Int)(
      implicit ec: ExecutionContext): Future[ValidationModel] =
    create(
      Organization(
        id = None,
        name = req.name,
        ownerUserId = userId,
        createdAt = OffsetDateTime.now,
        updatedAt = None
      ))

  override def createDBIO(item: Organization)(implicit ec: ExecutionContext): ModelDBIO =
    for {
      res <- super.createDBIO(item)
      _   <- OrganizationGroupsRepo.createAdminsDBIO(res.getId(), item.ownerUserId)
    } yield res

  // def update(id: Int, req: OrganizationUpdateRequest)(
  //     implicit ec: ExecutionContext): Future[ValidationModel] = {
  //   update(id)(_.copy(name = req.name))
  // }

  override def destroyDBIO(id: Int)(implicit ec: ExecutionContext) =
    for {
      _   <- PermissionsRepo.destroyByOrganizationIdDBIO(id)
      _   <- OrganizationGroupsRepo.destroyByOrganizationIdDBIO(id)
      _   <- ImagesRepo.destroyByOrganizationIdDBIO(id)
      res <- super.destroyDBIO(id)
    } yield res

  override def destroy(id: Int)(implicit ec: ExecutionContext) =
    runInTransaction(TransactionIsolation.Serializable)(destroyDBIO(id))

  private lazy val findSuggestionsCompiled = Compiled {
    (imageId: Rep[Int], orgId: Rep[Int], name: Rep[String], limit: ConstColumn[Long]) =>
      val groupsScope = OrganizationGroupsRepo
        .findSuggestionsDBIO(imageId, orgId, name)
        .map(t => (t.id, MemberKind.Group: Rep[MemberKind], t.name, None: Rep[Option[String]]))
        .subquery
      val usersScope = UsersRepo
        .findSuggestionsDBIO(imageId, name)
        .map(t => (t.id, MemberKind.User: Rep[MemberKind], t.username, t.fullName))
        .subquery
      groupsScope.union(usersScope).sortBy(_._3).take(limit)
  }

  def findSuggestions(imageId: Int, orgId: Int, q: String, limit: Long = 16L)(
      implicit ec: ExecutionContext): Future[Seq[PermissionMemberResponse]] = {
    val name = s"$q%".trim
    db.run(findSuggestionsCompiled((imageId, orgId, name, limit)).result)
      .map(_.map {
        case (id, MemberKind.User, username, fullName) =>
          PermissionUserMemberResponse(
            id = id,
            isOwner = false,
            username = username,
            fullName = fullName
          )
        case (id, MemberKind.Group, name, _) =>
          PermissionGroupMemberResponse(
            id = id,
            name = name
          )
      })
  }

  import Validations._

  private lazy val uniqueNameCompiled = Compiled { (id: Rep[Option[Int]], name: Rep[String]) =>
    exceptIdFilter(id).filter(_.name === name.asColumnOfType[String]("citext")).exists
  }

  override def validate(org: Organization)(implicit ec: ExecutionContext): ValidationDBIOResult = {
    val plainValidations = validatePlain(
      "name" -> List(lengthRange(org.name, 1, 255),
                     matches(org.name, Organization.nameRegEx, "invalid name format"))
    )
    val dbioValidations = validateDBIO(
      "name" -> List(unique(uniqueNameCompiled((org.id, org.name))))
    )
    validate((plainValidations, dbioValidations))
  }
}
