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

import io.fcomb.models.{ModelWithAutoIntPk, OwnerKind}
import java.time.ZonedDateTime

final case class Image(
    id: Option[Int],
    name: String,
    slug: String,
    ownerId: Int,
    ownerKind: OwnerKind,
    visibilityKind: ImageVisibilityKind,
    description: String,
    createdAt: ZonedDateTime,
    updatedAt: Option[ZonedDateTime]
) extends ModelWithAutoIntPk {
  def withPk(id: Int) = this.copy(id = Some(id))
}

object Image {
  val nameRegEx = """[\w-]+""".r
}

final case class DistributionImageCatalog(
    repositories: Seq[String]
)

sealed trait ImageKey

object ImageKey {
  final case class Id(id: Int)        extends ImageKey
  final case class Name(name: String) extends ImageKey
}
