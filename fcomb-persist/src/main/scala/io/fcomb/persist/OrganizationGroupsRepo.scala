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
import io.fcomb.FcombPostgresProfile.api._
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.models.{OrganizationGroup, Pagination, PaginationData}
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.rpc.helpers.OrganizationGroupHelpers
import io.fcomb.rpc.{OrganizationGroupRequest, OrganizationGroupResponse}
import io.fcomb.validations._
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.TransactionIsolation

class OrganizationGroupTable(tag: Tag)
    extends Table[OrganizationGroup](tag, "organization_groups")
    with PersistTableWithAutoIntPk {
  def organizationId = column[Int]("organization_id")
  def name           = column[String]("name")
  def role           = column[Role]("role")
  def createdAt      = column[OffsetDateTime]("created_at")
  def updatedAt      = column[Option[OffsetDateTime]]("updated_at")

  def * =
    (id, organizationId, name, role, createdAt, updatedAt) <>
      ((OrganizationGroup.apply _).tupled, OrganizationGroup.unapply)
}

object OrganizationGroupsRepo
    extends PersistModelWithAutoIntPk[OrganizationGroup, OrganizationGroupTable] {
  val table = TableQuery[OrganizationGroupTable]
  val label = "groups"

  lazy val findByNameCompiled = Compiled { name: Rep[String] =>
    table.filter(_.name === name.asColumnOfType[String]("citext")).take(1)
  }

  def findBySlugDBIO(slug: Slug) = {
    slug match {
      case Slug.Id(id)     => findByIdCompiled(id)
      case Slug.Name(name) => findByNameCompiled(name)
    }
  }

  def findBySlug(slug: Slug)(implicit ec: ExecutionContext): Future[Option[OrganizationGroup]] = {
    db.run(findBySlugDBIO(slug).result.headOption)
  }

  def findBySlugWithAcl(slug: Slug, userId: Int)(
      implicit ec: ExecutionContext): Future[Option[OrganizationGroup]] = {
    db.run {
      for {
        groupOpt <- findBySlugDBIO(slug).result.headOption
        res <- groupOpt match {
                case Some(group) =>
                  OrganizationsRepo
                    .isAdminDBIO(group.getId(), userId)
                    .map(isAdmin => if (isAdmin) groupOpt else None)
                case _ => DBIO.successful(None)
              }
      } yield res
    }
  }

  private def findByOrgIdDBIO(orgId: Rep[Int]) = {
    table.filter(_.organizationId === orgId)
  }

  private lazy val findByOrgIdCompiled = Compiled {
    (orgId: Rep[Int], offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
      findByOrgIdDBIO(orgId).sortBy(_.name).drop(offset).take(limit)
  }

  private lazy val findByOrgIdTotalCompiled = Compiled { orgId: Rep[Int] =>
    findByOrgIdDBIO(orgId).length
  }

  def paginateByOrgId(orgId: Int, p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[OrganizationGroupResponse]] = {
    db.run {
      for {
        groups <- findByOrgIdCompiled((orgId, p.offset, p.limit)).result
        total  <- findByOrgIdTotalCompiled(orgId).result
        data = groups.map(OrganizationGroupHelpers.responseFrom)
      } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit)
    }
  }

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

  override def updateDBIO(group: OrganizationGroup)(implicit ec: ExecutionContext,
                                                    m: Manifest[OrganizationGroup]) = {
    existAdminGroupApartFromDBIO(group.getId()).flatMap { exist =>
      if (exist) super.updateDBIO(group)
      else cannotUpdateAdminGroup
    }
  }

  private lazy val cannotUpdateAdminGroup =
    validationErrorAsDBIO("group", "Cannot update the last admin group")

  def update(id: Int, req: OrganizationGroupRequest)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    runInTransaction(TransactionIsolation.ReadCommitted) {
      existAdminGroupApartFromDBIO(id).flatMap { exist =>
        if (exist)
          updateDBIO(id)( // TODO: rewrite this with new validation api
            _.copy(
              name = req.name,
              role = req.role
            ))
        else cannotUpdateAdminGroup
      }
    }
  }

  def destroyDBIO(id: Int)(implicit ec: ExecutionContext) = {
    existAdminGroupApartFromDBIO(id).flatMap { exist =>
      if (exist) {
        for {
          _   <- PermissionsRepo.destroyByOrganizationGroupIdDBIO(id)
          res <- super.destroyDBIO(id)
        } yield res
      } else cannotDeleteAdminGroup
    }
  }

  override def destroy(id: Int)(implicit ec: ExecutionContext) = {
    runInTransaction(TransactionIsolation.Serializable)(destroyDBIO(id))
  }

  lazy val cannotDeleteAdminGroup =
    validationErrorAsDBIO("group", "Cannot delete the last admin group")

  def findIdsByOrganizationIdDBIO(organizationId: Int) = {
    findByOrgIdDBIO(organizationId).map(_.pk)
  }

  def destroyByOrganizationIdDBIO(organizationId: Int) = {
    table.filter(_.organizationId === organizationId).delete
  }

  def existAdminGroupApartFromDBIO(groupId: Int): DBIOAction[Boolean, NoStream, Effect.Read] = {
    table
      .join(table)
      .on(_.organizationId === _.organizationId)
      .join(OrganizationGroupUsersRepo.table)
      .on(_._2.id === _.groupId)
      .filter {
        case ((t, ogt), _) =>
          t.id === groupId && t.id =!= ogt.id && ogt.role === (Role.Admin: Role)
      }
      .exists
      .result
  }

  import Validations._

  // TODO: check name format

  private lazy val uniqueNameCompiled = Compiled {
    (id: Rep[Option[Int]], organizationId: Rep[Int], name: Rep[String]) =>
      exceptIdFilter(id).filter { q =>
        q.organizationId === organizationId && q.name === name.asColumnOfType[String]("citext")
      }.exists
  }

  override def validate(group: OrganizationGroup)(
      implicit ec: ExecutionContext): ValidationDBIOResult = {
    val plainValidations = validatePlain(
      "name" -> List(lengthRange(group.name, 1, 255))
    )
    val dbioValidations = validateDBIO(
      "name" -> List(unique(uniqueNameCompiled((group.id, group.organizationId, group.name))))
    )
    validate((plainValidations, dbioValidations))
  }
}
