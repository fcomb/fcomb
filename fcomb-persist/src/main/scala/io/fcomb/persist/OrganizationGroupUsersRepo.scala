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
import io.fcomb.models.OrganizationGroupUser

class OrganizationGroupUserTable(tag: Tag)
    extends Table[OrganizationGroupUser](tag, "organization_group_users") {
  def groupId = column[Int]("group_id")
  def userId  = column[Int]("user_id")

  def * = (groupId, userId) <> (OrganizationGroupUser.tupled, OrganizationGroupUser.unapply)
}

object OrganizationGroupUsersRepo
    extends PersistModel[OrganizationGroupUser, OrganizationGroupUserTable] {
  val table = TableQuery[OrganizationGroupUserTable]
}
