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

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.Organization
import java.time.ZonedDateTime

class OrganizationTable(tag: Tag)
    extends Table[Organization](tag, "organizations")
    with PersistTableWithAutoLongPk {
  def name            = column[String]("name")
  def createdByUserId = column[Long]("created_by_user_id")
  def createdAt       = column[ZonedDateTime]("created_at")
  def updatedAt       = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, name, createdByUserId, createdAt, updatedAt) <>
    ((Organization.apply _).tupled, Organization.unapply)
}

object OrganizationsRepo extends PersistModelWithAutoLongPk[Organization, OrganizationTable] {
  val table = TableQuery[OrganizationTable]
}
