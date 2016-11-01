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

import io.fcomb.Db.db
import io.fcomb.PostgresProfile.api._
import io.fcomb.models.docker.distribution.ImageManifestTag
import io.fcomb.models.{Pagination, PaginationData}
import io.fcomb.persist._
import io.fcomb.rpc.docker.distribution.RepositoryTagResponse
import java.time.OffsetDateTime
import scala.concurrent.{ExecutionContext, Future}

final class ImageManifestTagTable(_tag: Tag)
    extends Table[ImageManifestTag](_tag, "dd_image_manifest_tags") {
  def imageId         = column[Int]("image_id")
  def imageManifestId = column[Int]("image_manifest_id")
  def tag             = column[String]("tag")
  def updatedAt       = column[OffsetDateTime]("updated_at")

  def * =
    (imageId, imageManifestId, tag, updatedAt) <>
      ((ImageManifestTag.apply _).tupled, ImageManifestTag.unapply)
}

object ImageManifestTagsRepo
    extends PersistModel[ImageManifestTag, ImageManifestTagTable]
    with PaginationActions {
  val table = TableQuery[ImageManifestTagTable]
  val label = "tags"

  def upsertTagsDBIO(imageId: Int, imageManifestId: Int, tags: List[String])(
      implicit ec: ExecutionContext
  ) = {
    val timeNow = OffsetDateTime.now()
    for {
      existingTags <- findAllExistingTagsDBIO(imageId, imageManifestId, tags)
      _            <- DBIO.seq(existingTags.map(updateTagDBIO(_, imageManifestId)): _*)
      existingTagsSet = existingTags.map(_.tag).toSet
      newTags = tags
        .filterNot(existingTagsSet.contains)
        .map(t => ImageManifestTag(imageId, imageManifestId, t, timeNow))
      _ <- if (newTags.isEmpty) DBIO.successful(()) else table ++= newTags
    } yield ()
  }

  private def findAllExistingTagsDBIO(imageId: Int, imageManifestId: Int, tags: List[String]) =
    table
      .filter(t =>
        t.imageId === imageId && t.imageManifestId =!= imageManifestId && t.tag.inSetBind(tags))
      .result // .forUpdate

  private def updateTagDBIO(imt: ImageManifestTag, imageManifestId: Int)(
      implicit ec: ExecutionContext) =
    for {
      _ <- sqlu"""
          UPDATE #${ImageManifestsRepo.table.baseTableRow.tableName}
            SET tags = array_remove(tags, ${imt.tag}),
                updated_at = ${OffsetDateTime.now()}
            WHERE id = ${imt.imageManifestId}
          """
      _ <- table
        .filter { q =>
          q.imageId === imt.imageId && q.imageManifestId === imt.imageManifestId &&
          q.tag === imt.tag
        }
        .map(t => (t.imageManifestId, t.updatedAt))
        .update((imageManifestId, OffsetDateTime.now()))
    } yield ()

  private def findByImageIdDBIO(imageId: Rep[Int]) =
    table
      .join(ImageManifestsRepo.table)
      .on(_.imageManifestId === _.id)
      .filter(_._1.imageId === imageId)

  private def sortByPF(q: (Rep[String], Rep[String], Rep[Long], Rep[OffsetDateTime]))
    : PartialFunction[String, Rep[_]] = {
    case "tag"       => q._1
    case "digest"    => q._2
    case "length"    => q._3
    case "updatedAt" => q._4
  }

  private def findByImageIdAsReponseDBIO(imageId: Int, p: Pagination) = {
    val q = findByImageIdDBIO(imageId).map {
      case (t, imt) => (t.tag, imt.digest, imt.length, t.updatedAt)
    }
    sortByQuery(q, p)(sortByPF, _._4.desc).drop(p.offset).take(p.limit)
  }

  private lazy val findByImageIdTotalCompiled = Compiled { imageId: Rep[Int] =>
    findByImageIdDBIO(imageId).length
  }

  def paginateByImageId(imageId: Int, p: Pagination)(
      implicit ec: ExecutionContext): Future[PaginationData[RepositoryTagResponse]] =
    db.run {
      for {
        tags  <- findByImageIdAsReponseDBIO(imageId, p).result
        total <- findByImageIdTotalCompiled(imageId).result
        data = tags.map(t => RepositoryTagResponse.tupled(t.copy(_4 = t._4.toString)))
      } yield PaginationData(data, total = total, offset = p.offset, limit = p.limit)
    }
}
