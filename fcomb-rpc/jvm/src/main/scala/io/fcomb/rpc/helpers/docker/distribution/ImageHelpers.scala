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

package io.fcomb.rpc.helpers.docker.distribution

import io.fcomb.models.docker.distribution.Image
import io.fcomb.rpc.docker.distribution.RepositoryResponse
import io.fcomb.rpc.helpers.time.Implicits._

// TODO: direct mapping within repo instead of excess conversion
object ImageHelpers {
  def responseFrom(image: Image): RepositoryResponse =
    RepositoryResponse(
      id = image.id,
      name = image.name,
      slug = image.slug,
      owner = image.owner,
      visibilityKind = image.visibilityKind,
      description = image.description,
      createdAt = image.createdAt.toIso8601,
      updatedAt = image.updatedAt.map(_.toIso8601)
    )
}
