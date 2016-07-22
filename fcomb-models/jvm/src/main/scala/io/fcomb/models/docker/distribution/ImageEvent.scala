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

package io.fcomb.models.docker.distribution

import io.fcomb.models.ModelWithAutoIntPk
import java.time.ZonedDateTime

sealed trait ImageEventDetails {}

object ImageEventDetails {
  final case class Upserted(
      name: String,
      slug: String,
      visibilityKind: ImageVisibilityKind,
      tags: List[String],
      length: Long
  ) extends ImageEventDetails
}

final case class ImageEvent(
    id: Option[Int] = None,
    imageManifestId: Int,
    kind: ImageEventKind,
    detailsJsonBlob: String,
    createdAt: ZonedDateTime
) extends ModelWithAutoIntPk {
  def withPk(id: Int) = this.copy(id = Some(id))
}

object ImageEvent {}
