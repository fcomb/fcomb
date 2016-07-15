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

import io.fcomb.Db._
import io.fcomb.FcombPostgresProfile.api._
import io.fcomb.models.docker.distribution.ImageWebhook
import io.fcomb.persist.{PersistModelWithAutoIntPk, PersistTableWithAutoIntPk}

import scala.concurrent.{ExecutionContext, Future}

class ImageWebhookTable(tag: Tag)
    extends Table[ImageWebhook](tag, "dd_image_webhooks")
    with PersistTableWithAutoIntPk {
  def imageId = column[Int]("image_id")
  def url     = column[String]("url")

  def * =
    (id, imageId, url) <>
      ((ImageWebhook.apply _).tupled, ImageWebhook.unapply)
}

object ImageWebhooksRepo extends PersistModelWithAutoIntPk[ImageWebhook, ImageWebhookTable] {
  val table = TableQuery[ImageWebhookTable]

  def create(imageId: Int, url: String)(implicit ec: ExecutionContext): Future[ValidationModel] = {
    super.create(
      ImageWebhook(
        id = None,
        imageId = imageId,
        url = url
      )
    )
  }

  private lazy val findByImageIdCompiled = Compiled { imageId: Rep[Int] =>
    table.filter(_.imageId === imageId)
  }

  def findByImageId(imageId: Int) =
    db.run(findByImageIdCompiled(imageId).result)

}
