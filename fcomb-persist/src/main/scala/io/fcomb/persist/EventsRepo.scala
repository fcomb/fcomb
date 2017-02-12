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

import io.circe.Json
import io.circe.syntax._
import io.fcomb.Db.db
import io.fcomb.PostgresProfile.api._
import io.fcomb.PostgresProfile.createJdbcMapping
import io.fcomb.json.models.Formats._
import io.fcomb.models.{Event, EventDetails, EventKind}
import java.time.OffsetDateTime

object EventTableImplicits {
  implicit val eventKindColumnType = createJdbcMapping("event_kind", EventKind)
}
import EventTableImplicits._

final class EventTable(tag: Tag)
    extends Table[Event](tag, "dd_events")
    with PersistTableWithAutoIntPk {
  def kind            = column[EventKind]("kind")
  def detailsJsonBlob = column[Json]("details_json_blob")
  def createdByUserId = column[Int]("created_by")
  def createdAt       = column[OffsetDateTime]("created_at")

  def * =
    (id.?, kind, detailsJsonBlob, createdByUserId, createdAt) <>
      ({
        case (id, kind, detailsJsonBlob, createdByUserId, createdAt) =>
          val Right(details) = detailsJsonBlob.as[EventDetails]
          Event(id, kind, details, createdByUserId, createdAt)
      }, { evt: Event =>
        val detailsJsonBlob = evt.details.asJson
        Some((evt.id, evt.kind, detailsJsonBlob, evt.createdByUserId, evt.createdAt))
      })
}

object EventsRepo extends PersistModelWithAutoIntPk[Event, EventTable] {
  val table = TableQuery[EventTable]

  def createDBIO(details: EventDetails, createdByUserId: Int) =
    tableWithPk += Event(
      id = None,
      kind = details.kind,
      details = details,
      createdByUserId = createdByUserId,
      createdAt = OffsetDateTime.now
    )

  def create(details: EventDetails, createdByUserId: Int) =
    db.run(createDBIO(details, createdByUserId))

  def createRepoEventDBIO(repoId: Int, name: String, slug: String, createdByUserId: Int) = {
    val details = EventDetails.CreateRepo(
      repoId = repoId,
      name = name,
      slug = slug
    )
    createDBIO(details, createdByUserId)
  }
}
