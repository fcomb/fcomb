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

package io.fcomb.json.models.docker.distribution

import enumeratum.Circe
import io.circe.generic.semiauto._
import io.circe.{Encoder, Decoder}
import io.fcomb.models.docker.distribution._

object Formats {
  implicit final val encodeImageVisibilityKind: Encoder[ImageVisibilityKind] =
    Circe.encoder(ImageVisibilityKind)
  implicit final val encodeImageEventKind: Encoder[ImageEventKind] = Circe.encoder(ImageEventKind)
  implicit final val decodeImageVisibilityKind: Decoder[ImageVisibilityKind] =
    Circe.decoder(ImageVisibilityKind)

  implicit final val encodeDistributionImageCatalog: Encoder[DistributionImageCatalog] =
    deriveEncoder
  implicit final val encodeImageTagsResponse: Encoder[ImageTagsResponse] = deriveEncoder

  implicit final val decodeImageEventKind: Decoder[ImageEventKind] = Circe.decoder(ImageEventKind)

  implicit final val decodeDistributionImageCatalog: Decoder[DistributionImageCatalog] =
    deriveDecoder
  implicit final val decodeImageTagsResponse: Decoder[ImageTagsResponse] = deriveDecoder
}
