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
import cats.syntax.eq._
import io.fcomb.Db.db
import io.fcomb.PostgresProfile.api._
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.models.{OrganizationGroup, Pagination, PaginationData}
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.PaginationActions._
import io.fcomb.rpc.helpers.OrganizationGroupHelpers
import io.fcomb.rpc.{OrganizationGroupRequest, OrganizationGroupResponse}
import io.fcomb.validation._
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

final class OrganizationGroupTable(tag: Tag)
    extends Table[OrganizationGroup](tag, "organization_groups")
    with PersistTableWithAutoIntPk {
  def organizationId = column[Int]("organization_id")
  def name           = column[String]("name")
  def role           = column[Role]("role")
  def createdAt      = column[OffsetDateTime]("created_at")
  def updatedAt      = column[Option[OffsetDateTime]]("updated_at")

  def * =
    (id.?, organizationId, name, role, createdAt, updatedAt) <>
      ((OrganizationGroup.apply _).tupled, OrganizationGroup.unapply)
}

object OrganizationGroupsRepo
    extends PersistModelWithAutoIntPk[OrganizationGroup, OrganizationGroupTable] {
  val table = TableQuery[OrganizationGroupTable]
  val label = "groups"

  private lazy val findByOrgIdAndIdCompiled = Compiled { (orgId: Rep[Int], id: Rep[Int]) =>
    table.filter(t => t.organizationId === orgId && t.id === id).take(1)
  }

  private lazy val findByOrgIdAndNameCompiled = Compiled { (orgId: Rep[Int], name: Rep[String]) =>
    table
      .filter { t =>
        t.organizationId === orgId && t.name === name.asColumnOfType[String]("citext")
      }
      .take(1)
  }

  def findBySlugDBIO(orgId: Int, slug: Slug) =
    slug match {
      case Slug.Id(id)     => findByOrgIdAndIdCompiled((orgId, id))
      case Slug.Name(name) => findByOrgIdAndNameCompiled((orgId, name))
    }

  def findBySlug(orgId: Int, slug: Slug)(
      implicit ec: ExecutionContext): Future[Option[OrganizationGroup]] =
    db.run(findBySlugDBIO(orgId, slug).result.headOption)

  private def findByOrgIdDBIO(orgId: Rep[Int]) =
    table.filter(_.organizationId === orgId)

  private lazy val findByOrgIdTotalCompiled = Compiled { orgId: Rep[Int] =>
    findByOrgIdDBIO(orgId).length
  }

  def findByIdAsValidatedDBIO(orgId: Int, id: Int)(implicit ec: ExecutionContext) =
    findByOrgIdAndIdCompiled((orgId, id)).result.headOption.map {
      case Some(g) => Validated.Valid(g)
      case _       => validationError("member.id", "Not found")
    }

  def findByNameAsValidatedDBIO(orgId: Int, name: String)(implicit ec: ExecutionContext) =
    findByOrgIdAndNameCompiled((orgId, name)).result.headOption.map {
      case Some(g) => Validated.Valid(g)
      case _       => validationError("member.name", "Not found")
    }

  def findBySlugAsValidatedDBIO(orgId: Int, slug: Slug)(implicit ec: ExecutionContext)
    : DBIOAction[ValidationResult[OrganizationGroup], NoStream, Effect.Read] =
    slug match {
      case Slug.Id(id)     => findByIdAsValidatedDBIO(orgId, id)
      case Slug.Name(name) => findByNameAsValidatedDBIO(orgId, name)
    }

  private def sortByPF(q: OrganizationGroupTable): PartialFunction[String, Rep[_]] = {
    case "id"   => q.id
    case "name" => q.name
    case "role" => q.role
  }

  def paginate(orgId: Int, p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[OrganizationGroupResponse]] =
    db.run(for {
      groups <- sortPaginate(findByOrgIdDBIO(orgId), p)(sortByPF, _.name).result
      total  <- findByOrgIdTotalCompiled(orgId).result
      data = groups.map(OrganizationGroupHelpers.response)
    } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit))

  def createAdminsDBIO(organizationId: Int, ownerUserId: Int)(implicit ec: ExecutionContext) = {
    val group = OrganizationGroup(
      id = None,
      organizationId = organizationId,
      name = "Admins",
      role = Role.Admin,
      createdAt = OffsetDateTime.now(),
      updatedAt = None
    )
    for {
      og <- createDBIO(group)
      _  <- OrganizationGroupUsersRepo.createDBIO(og.getId(), ownerUserId)
    } yield og
  }

  def create(organizationId: Int, req: OrganizationGroupRequest)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    val group = OrganizationGroup(
      id = None,
      organizationId = organizationId,
      name = req.name,
      role = req.role,
      createdAt = OffsetDateTime.now(),
      updatedAt = None
    )
    create(group)
  }

  private lazy val cannotUpdateAdminGroupRole =
    validationErrorAsDBIO("role", "Cannot downgrade role of the last admin group")

  def updateDBIO(id: Int, userId: Int)(f: OrganizationGroup => OrganizationGroup)(
      implicit ec: ExecutionContext) =
    findByIdQuery(id).result.headOption.flatMap {
      case Some(item) =>
        val updatedItem = f(item)
        if (item.role === Role.Admin && updatedItem.role =!= Role.Admin) {
          existsAdminGroupApartFromDBIO(id, userId).flatMap {
            case true => updateDBIOWithValidation(updatedItem)
            case _    => cannotUpdateAdminGroupRole
          }
        } else updateDBIOWithValidation(updatedItem)
      case _ => DBIO.successful(recordNotFound(id))
    }

  def update(id: Int, userId: Int, req: OrganizationGroupRequest)(
      implicit ec: ExecutionContext): Future[ValidationModel] =
    runInTransaction(TransactionIsolation.ReadCommitted) {
      updateDBIO(id, userId)(
        _.copy(
          name = req.name,
          role = req.role
        ))
    }

  def destroyDBIO(id: Int, userId: Int)(
      implicit ec: ExecutionContext): DBIO[ValidationResultUnit] =
    existsAdminGroupApartFromDBIO(id, userId).flatMap {
      case true =>
        for {
          _   <- PermissionsRepo.destroyByOrganizationGroupIdDBIO(id)
          res <- super.destroyDBIO(id)
        } yield Validated.Valid(())
      case _ => cannotDeleteAdminGroup
    }

  def destroy(id: Int, userId: Int)(implicit ec: ExecutionContext) =
    runInTransaction(TransactionIsolation.Serializable)(destroyDBIO(id, userId))

  private lazy val cannotDeleteAdminGroup =
    validationErrorAsDBIO("id", "Cannot delete your last admin group")

  def findIdsByOrganizationIdDBIO(organizationId: Int) =
    findByOrgIdDBIO(organizationId).map(_.id)

  def destroyByOrganizationIdDBIO(organizationId: Int) =
    table.filter(_.organizationId === organizationId).delete

  def existsAdminGroupApartFromDBIO(groupId: Int,
                                    userId: Int): DBIOAction[Boolean, NoStream, Effect.Read] =
    table
      .join(table)
      .on(_.organizationId === _.organizationId)
      .join(OrganizationGroupUsersRepo.table)
      .on(_._2.id === _.groupId)
      .filter {
        case ((tbl, secondTbl), groupUsersTbl) =>
          tbl.id === groupId &&
            tbl.id =!= secondTbl.id &&
            secondTbl.role === (Role.Admin: Role) &&
            groupUsersTbl.userId === userId
      }
      .exists
      .result

  def findSuggestionsDBIO(imageId: Rep[Int], orgId: Rep[Int], name: Rep[String]) =
    table.filter { t =>
      t.organizationId === orgId &&
      t.name.like(name.asColumnOfType[String]("citext")) &&
      !t.id.in(PermissionsRepo.findGroupMemberIdsByImageIdDBIO(imageId))
    }

  import Validations._

  private lazy val uniqueNameCompiled = Compiled {
    (id: Rep[Option[Int]], organizationId: Rep[Int], name: Rep[String]) =>
      exceptIdFilter(id)
        .filter { q =>
          q.organizationId === organizationId && q.name === name.asColumnOfType[String]("citext")
        }
        .exists
  }

  override def validate(group: OrganizationGroup)(
      implicit ec: ExecutionContext): ValidationDBIOResult = {
    val plainValidations = validatePlain(
      "name" -> List(lengthRange(group.name, 1, 255),
                     matches(group.name, OrganizationGroup.nameRegEx, "invalid name format"))
    )
    val dbioValidations = validateDBIO(
      "name" -> List(unique(uniqueNameCompiled((group.id, group.organizationId, group.name))))
    )
    validate((plainValidations, dbioValidations))
  }
}
