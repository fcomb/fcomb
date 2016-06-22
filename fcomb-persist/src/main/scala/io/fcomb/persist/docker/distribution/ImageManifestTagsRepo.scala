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

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.ImageManifestTag
import io.fcomb.persist._
import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext

class ImageManifestTagTable(_tag: Tag)
    extends Table[ImageManifestTag](_tag, "dd_image_manifest_tags") {
  def imageId         = column[Long]("image_id")
  def imageManifestId = column[Long]("image_manifest_id")
  def tag             = column[String]("tag")

  def * =
    (imageId, imageManifestId, tag) <>
      ((ImageManifestTag.apply _).tupled, ImageManifestTag.unapply)
}

object ImageManifestTagsRepo extends PersistModel[ImageManifestTag, ImageManifestTagTable] {
  val table = TableQuery[ImageManifestTagTable]

  def upsertTagsDBIO(imageId: Long, imageManifestId: Long, tags: List[String])(
      implicit ec: ExecutionContext
  ) = {
    for {
      existingTags <- findAllExistingTagsDBIO(imageId, imageManifestId, tags)
      _            <- DBIO.seq(existingTags.map(updateTagDBIO(_, imageManifestId)): _*)
      existingTagsSet = existingTags.map(_.tag).toSet
      newTags = tags
        .filterNot(existingTagsSet.contains)
        .map(t => ImageManifestTag(imageId, imageManifestId, t))
      _ <- {
        if (newTags.isEmpty) DBIO.successful(())
        else table ++= newTags
      }
    } yield ()
  }

  private def findAllExistingTagsDBIO(imageId: Long, imageManifestId: Long, tags: List[String]) =
    table.filter { q =>
      q.imageId === imageId && q.imageManifestId =!= imageManifestId && q.tag.inSetBind(tags)
    }
    // .forUpdate
    .result

  private def updateTagDBIO(imt: ImageManifestTag, imageManifestId: Long)(
      implicit ec: ExecutionContext
  ) = {
    for {
      _ <- sqlu"""
          UPDATE #${ImageManifestsRepo.table.baseTableRow.tableName}
            SET tags = array_remove(tags, ${imt.tag}),
                updated_at = ${ZonedDateTime.now()}
            WHERE id = ${imt.imageManifestId}
          """
      _ <- table.filter { q =>
            q.imageId === imt.imageId && q.imageManifestId === imt.imageManifestId &&
            q.tag === imt.tag
          }.map(_.imageManifestId).update(imageManifestId)
    } yield ()
  }
}
