package io.fcomb.persist.docker.distribution

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{ImageManifestTag ⇒ MImageManifestTag}
import io.fcomb.persist._
import java.time.ZonedDateTime
import scala.concurrent.ExecutionContext

class ImageManifestTagTable(_tag: Tag) extends Table[MImageManifestTag](_tag, "dd_image_manifest_tags") {
  def imageId = column[Long]("image_id")
  def imageManifestId = column[Long]("image_manifest_id")
  def tag = column[String]("tag")

  def * =
    (imageId, imageManifestId, tag) <>
      ((MImageManifestTag.apply _).tupled, MImageManifestTag.unapply)
}

object ImageManifestTag extends PersistModel[MImageManifestTag, ImageManifestTagTable] {
  val table = TableQuery[ImageManifestTagTable]

  def upsertTagsDBIO(imageId: Long, imageManifestId: Long, tags: List[String])(
    implicit
    ec: ExecutionContext
  ) = {
    for {
      existingTags ← findAllExistingTagsDBIO(imageId, imageManifestId, tags)
      _ ← DBIO.seq(existingTags.map(updateTagDBIO(_, imageManifestId)): _*)
      existingTagsSet = existingTags.map(_.tag).toSet
      newTags = tags.filterNot(existingTagsSet.contains)
        .map(t ⇒ MImageManifestTag(imageId, imageManifestId, t))
      _ ← table ++= newTags
    } yield ()
  }

  private def findAllExistingTagsDBIO(imageId: Long, imageManifestId: Long, tags: List[String]) =
    table
      .filter { q ⇒
        q.imageId === imageId &&
          q.imageManifestId =!= imageManifestId &&
          q.tag.inSetBind(tags)
      }
      .result

  private def updateTagDBIO(imt: MImageManifestTag, imageManifestId: Long)(
    implicit
    ec: ExecutionContext
  ) = {
    for {
      _ ← sqlu"""
          UPDATE #${ImageManifest.table.baseTableRow.tableName}
            SET tags = array_remove(tags, ${imt.tag}),
                updated_at = ${ZonedDateTime.now()}
            WHERE id = ${imt.imageManifestId}
          """
      _ ← table
        .filter { q ⇒
          q.imageId === imt.imageId &&
            q.imageManifestId === imt.imageManifestId &&
            q.tag === imt.tag
        }
        .map(_.imageManifestId)
        .update(imageManifestId)
    } yield ()
  }
}
