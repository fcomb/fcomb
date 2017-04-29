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

package io.fcomb.persist.docker.distribution

import io.fcomb.PostgresProfile.api._
import io.fcomb.models.docker.distribution.ImageManifestLayer
import io.fcomb.persist._
import java.util.UUID

final class ImageManifestLayerTable(_tag: Tag)
    extends Table[ImageManifestLayer](_tag, "dd_image_manifest_layers") {
  def imageManifestId = column[Int]("image_manifest_id")
  def layerBlobId     = column[UUID]("layer_blob_id")

  def * = (imageManifestId, layerBlobId).mapTo[ImageManifestLayer]
}

object ImageManifestLayersRepo extends PersistModel[ImageManifestLayer, ImageManifestLayerTable] {
  val table = TableQuery[ImageManifestLayerTable]

  def insertLayersDBIO(imageManifestId: Int, layers: List[UUID]) =
    if (layers.isEmpty) DBIO.successful(())
    else table ++= layers.map(l => ImageManifestLayer(imageManifestId, l))

  lazy val isBlobLinkedC = Compiled { id: Rep[UUID] =>
    table.filter(_.layerBlobId === id).exists
  }
}
