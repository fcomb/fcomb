package io.fcomb.persist.docker.distribution

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.{ImageManifestTag ⇒ MImageManifestTag}
import io.fcomb.persist._

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

  def upsertTagsDBIO(imageId: Long, imageManifestId: Long, tags: List[String]) = {
    table ++= tags.map(t ⇒ MImageManifestTag(imageId, imageManifestId, t))
  }
}
