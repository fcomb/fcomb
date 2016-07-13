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
import io.fcomb.persist.EnumsMapping._
import io.fcomb.rpc.{OrganizationCreateRequest, OrganizationUpdateRequest}
import io.fcomb.validations._
import java.time.ZonedDateTime
import scala.concurrent.{Future, ExecutionContext}

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

  def groupUsersScope =
    table
      .join(OrganizationGroupsRepo.table)
      .on(_.id === _.organizationId)
      .join(OrganizationGroupUsersRepo.table)
      .on(_._2.id === _.groupId)

  lazy val isAdminCompiled = Compiled { (id: Rep[Int], userId: Rep[Int]) =>
    groupUsersScope.filter {
      case ((t, ogt), ogut) =>
        t.id === id && ogt.role === (Role.Admin: Role) && ogut.userId === userId
    }.exists
  }

  def isAdminDBIO(id: Int, userId: Int): DBIOAction[Boolean, NoStream, Effect.Read] = {
    isAdminCompiled((id, userId)).result
  }

  def isAdmin(id: Int, userId: Int): Future[Boolean] = {
    db.run(isAdminDBIO(id, userId))
  }

  import Validations._

  private lazy val uniqueNameCompiled = Compiled { (id: Rep[Option[Int]], name: Rep[String]) =>
    notCurrentPkFilter(id).filter(_.name === name).exists
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
