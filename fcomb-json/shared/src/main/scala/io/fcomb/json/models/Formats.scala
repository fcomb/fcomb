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

package io.fcomb.json.models

import enumeratum.Circe
import io.circe.generic.semiauto._
import io.circe.{Encoder, Decoder}
import io.fcomb.models._

object Formats {
  implicit final val encodeOwnerKind: Encoder[OwnerKind] = Circe.encoder(OwnerKind)

  implicit final val encodeSession: Encoder[Session] = deriveEncoder
  implicit final def encodePaginationData[T](
      implicit encoder: Encoder[T]): Encoder[PaginationData[T]] =
    deriveEncoder

  implicit final val decodeOwnerKind: Decoder[OwnerKind] = Circe.decoder(OwnerKind)

  implicit final val decodeSession: Decoder[Session] = deriveDecoder
  implicit final def decodePaginationData[T](
      implicit decoder: Decoder[T]): Decoder[PaginationData[T]] =
    deriveDecoder
}
