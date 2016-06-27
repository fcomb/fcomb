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
import io.fcomb.models.{Application, ApplicationState, OwnerKind}
import io.fcomb.persist.EnumsMapping._
import java.time.ZonedDateTime

class ApplicationTable(tag: Tag)
    extends Table[Application](tag, "applications")
    with PersistTableWithAutoIntPk {
  def name      = column[String]("name")
  def state     = column[ApplicationState]("state")
  def token     = column[String]("token")
  def ownerId   = column[Int]("owner_id")
  def ownerKind = column[OwnerKind]("owner_kind")

  def createdAt = column[ZonedDateTime]("created_at")
  def updatedAt = column[Option[ZonedDateTime]]("updated_at")

  def * =
    (id, name, state, token, ownerId, ownerKind, createdAt, updatedAt) <>
      ((Application.apply _).tupled, Application.unapply)
}

object ApplicationsRepo extends PersistModelWithAutoIntPk[Application, ApplicationTable] {
  val table = TableQuery[ApplicationTable]
}
