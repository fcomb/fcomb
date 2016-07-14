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
import io.fcomb.models.Organization
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.persist.EnumsMapping._
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.persist.docker.distribution.ImageBlobsRepo
import io.fcomb.rpc.{OrganizationCreateRequest, OrganizationUpdateRequest}
import io.fcomb.validations._
import java.time.ZonedDateTime
import scala.concurrent.{Future, ExecutionContext}
import slick.jdbc.TransactionIsolation

class OrganizationTable(tag: Tag)
    extends Table[Organization](tag, "organizations")
    with PersistTableWithAutoIntPk {
  def name        = column[String]("name")
  def ownerUserId = column[Int]("owner_user_id")
  def createdAt   = column[ZonedDateTime]("created_at")
  def updatedAt   = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, name, ownerUserId, createdAt, updatedAt) <>
      ((Organization.apply _).tupled, Organization.unapply)
}

object OrganizationsRepo extends PersistModelWithAutoIntPk[Organization, OrganizationTable] {
  val table = TableQuery[OrganizationTable]

  lazy val findByNameCompiled = Compiled { name: Rep[String] =>
    table.filter(_.name === name.asColumnOfType[String]("citext")).take(1)
  }

  def findBySlugDBIO(slug: Slug) = {
    slug match {
      case Slug.Id(id)     => findByIdCompiled(id)
      case Slug.Name(name) => findByNameCompiled(name)
    }
  }

  def findBySlug(slug: Slug)(implicit ec: ExecutionContext): Future[Option[Organization]] = {
    db.run(findBySlugDBIO(slug).result.headOption)
  }

  def findBySlugWithAclDBIO(slug: Slug, userId: Int, role: Role)(implicit ec: ExecutionContext) = {
    findBySlugDBIO(slug).result.headOption.flatMap(mapWithAclDBIO(_, userId, role))
  }

  def findBySlugWithAcl(slug: Slug, userId: Int, role: Role)(
      implicit ec: ExecutionContext): Future[Option[Organization]] = {
    db.run(findBySlugWithAclDBIO(slug, userId, role))
  }

  def groupUsersScope =
    table
      .join(OrganizationGroupsRepo.table)
      .on(_.id === _.organizationId)
      .join(OrganizationGroupUsersRepo.table)
      .on(_._2.id === _.groupId)

  private def userGroupScope(id: Rep[Int], userId: Rep[Int]) = {
    groupUsersScope.filter {
      case ((t, _), ogut) =>
        t.id === id && ogut.userId === userId
    }
  }

  lazy val hasRoleCompiled = Compiled { (id: Rep[Int], userId: Rep[Int], role: Rep[Role]) =>
    userGroupScope(id, userId).filter { case ((_, ogt), _) => ogt.role === role }.exists
  }

  def mapWithAclDBIO(orgOpt: Option[Organization], userId: Int, role: Role)(
      implicit ec: ExecutionContext): DBIOAction[Option[Organization], NoStream, Effect.Read] = {
    orgOpt match {
      case Some(org) =>
        hasRoleCompiled((org.getId(), userId, role)).result.map { hasRole =>
          if (hasRole) orgOpt else None
        }
      case _ => DBIO.successful(None)
    }
  }

  def isAdminDBIO(id: Int, userId: Int) = {
    hasRoleCompiled((id, userId, Role.Admin)).result
  }

  def isAdmin(id: Int, userId: Int): Future[Boolean] = {
    db.run(isAdminDBIO(id, userId))
  }

  def create(req: OrganizationCreateRequest, userId: Int)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    create(
      Organization(
        id = None,
        name = req.name,
        ownerUserId = userId,
        createdAt = ZonedDateTime.now,
        updatedAt = None
      ))
  }

  override def createDBIO(item: Organization)(implicit ec: ExecutionContext): ModelDBIO = {
    for {
      res <- super.createDBIO(item)
      _   <- OrganizationGroupsRepo.createAdminsDBIO(res.getId(), item.ownerUserId)
    } yield res
  }

  def update(id: Int, req: OrganizationUpdateRequest)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    update(id)(_.copy(name = req.name))
  }

  def destroyDBIO(id: Int)(implicit ec: ExecutionContext) = {
    for {
      _   <- PermissionsRepo.destroyByOrganizationIdDBIO(id)
      _   <- OrganizationGroupsRepo.destroyByOrganizationIdDBIO(id)
      _   <- ImageBlobsRepo.destroyByOrganizationIdDBIO(id)
      res <- super.destroyDBIO(id)
    } yield res
  }

  override def destroy(id: Int)(implicit ec: ExecutionContext) = {
    runInTransaction(TransactionIsolation.Serializable)(destroyDBIO(id))
  }

  import Validations._

  private lazy val uniqueNameCompiled = Compiled { (id: Rep[Option[Int]], name: Rep[String]) =>
    exceptIdFilter(id).filter(_.name === name.asColumnOfType[String]("citext")).exists
  }

  override def validate(org: Organization)(implicit ec: ExecutionContext): ValidationDBIOResult = {
    val plainValidations = validatePlain(
      "name" -> List(lengthRange(org.name, 1, 255))
    )
    val dbioValidations = validateDBIO(
      "name" -> List(unique(uniqueNameCompiled((org.id, org.name))))
    )
    validate((plainValidations, dbioValidations))
  }
}
