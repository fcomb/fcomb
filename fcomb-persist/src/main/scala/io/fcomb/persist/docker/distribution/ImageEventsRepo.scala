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

package io.fcomb.persist.docker.distribution

import java.time.ZonedDateTime

import io.fcomb.FcombPostgresProfile.api._
import io.fcomb.models.docker.distribution.{ImageEvent, ImageEventKind}
import io.fcomb.persist.{PersistModelWithAutoIntPk, PersistTableWithAutoIntPk}
import io.fcomb.persist.EnumsMapping.distributionImageEventKindColumnType

import scala.concurrent.{ExecutionContext, Future}

class ImageEventTable(tag: Tag)
    extends Table[ImageEvent](tag, "dd_image_events")
    with PersistTableWithAutoIntPk {
  def imageManifestId = column[Int]("image_manifest_id")
  def kind            = column[ImageEventKind]("kind")
  def detailsJsonBlob = column[String]("details_json_blob")
  def createdAt       = column[ZonedDateTime]("created_at")

  def * =
    (id, imageManifestId, kind, detailsJsonBlob, createdAt) <>
      ((ImageEvent.apply _).tupled, ImageEvent.unapply)
}

object ImageEventsRepo extends PersistModelWithAutoIntPk[ImageEvent, ImageEventTable] {
  val table = TableQuery[ImageEventTable]

  def create(imageManifestId: Int, kind: ImageEventKind, detailsJsonBlob: String)(
      implicit ec: ExecutionContext): Future[ValidationModel] = {
    super.create(
      ImageEvent(
        id = None,
        imageManifestId = imageManifestId,
        kind = kind,
        detailsJsonBlob = detailsJsonBlob,
        createdAt = ZonedDateTime.now()
      )
    )
  }
}
