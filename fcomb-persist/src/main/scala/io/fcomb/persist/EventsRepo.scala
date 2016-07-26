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

import cats.data.Xor
import io.circe.Json
import io.circe.syntax._
import io.fcomb.Db.db
import io.fcomb.FcombPostgresProfile.api._
import io.fcomb.FcombPostgresProfile.createJdbcMapping
import io.fcomb.json.models.Formats._
import io.fcomb.models.{Event, EventDetails, EventKind}
import org.threeten.bp.ZonedDateTime

object EventTableImplicits {
  implicit val eventKindColumnType = createJdbcMapping("event_kind", EventKind)
}
import EventTableImplicits._

class EventTable(tag: Tag) extends Table[Event](tag, "dd_events") with PersistTableWithAutoIntPk {
  def kind            = column[EventKind]("kind")
  def detailsJsonBlob = column[Json]("details_json_blob")
  def createdBy       = column[Int]("created_by")
  def createdAt       = column[ZonedDateTime]("created_at")

  def * =
    (id, kind, detailsJsonBlob, createdBy, createdAt) <>
      ({
        case (id, kind, detailsJsonBlob, createdBy, createdAt) =>
          val details = detailsJsonBlob.as[EventDetails] match {
            case Xor.Right(evt) => evt
            case Xor.Left(e)    => throw e
          }
          Event(id, kind, details, createdBy, createdAt)
      }, { evt: Event =>
        val detailsJsonBlob = evt.details.asJson
        Some((evt.id, evt.kind, detailsJsonBlob, evt.createdBy, evt.createdAt))
      })
}

object EventsRepo extends PersistModelWithAutoIntPk[Event, EventTable] {
  val table = TableQuery[EventTable]

  def createDBIO(details: EventDetails, createdBy: Int) = {
    tableWithPk += Event(
      id = None,
      kind = details.kind,
      details = details,
      createdBy = createdBy,
      createdAt = ZonedDateTime.now
    )
  }

  def create(details: EventDetails, createdBy: Int) = {
    db.run(createDBIO(details, createdBy))
  }

  def createRepoEventDBIO(repoId: Int, name: String, slug: String, createdBy: Int) = {
    val details = EventDetails.CreateRepo(
      repoId = repoId,
      name = name,
      slug = slug
    )
    createDBIO(details, createdBy)
  }
}
