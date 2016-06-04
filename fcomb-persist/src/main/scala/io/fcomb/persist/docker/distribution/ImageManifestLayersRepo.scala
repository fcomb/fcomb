package io.fcomb.persist.docker.distribution

import io.fcomb.RichPostgresDriver.api._
import io.fcomb.models.docker.distribution.ImageManifestLayer
import io.fcomb.persist._
import java.util.UUID

class ImageManifestLayerTable(_tag: Tag) extends Table[ImageManifestLayer](_tag, "dd_image_manifest_layers") {
  def imageManifestId = column[Long]("image_manifest_id")
  def layerBlobId = column[UUID]("layer_blob_id")

  def * =
    (imageManifestId, layerBlobId) <>
      ((ImageManifestLayer.apply _).tupled, ImageManifestLayer.unapply)
}

object ImageManifestLayersRepo extends PersistModel[ImageManifestLayer, ImageManifestLayerTable] {
  val table = TableQuery[ImageManifestLayerTable]

  def insertLayersDBIO(imageManifestId: Long, layers: List[UUID]) = {
    if (layers.isEmpty) DBIO.successful(())
    else table ++= layers.map(l ⇒ ImageManifestLayer(imageManifestId, l))
  }

  val isBlobLinkedCompiled = Compiled { id: Rep[UUID] ⇒
    table.filter(_.layerBlobId === id).exists
  }
}
