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

package io.fcomb.json.models.errors.docker.distribution

import enumeratum.Circe
import io.circe.generic.semiauto._
import io.circe.{Encoder, Decoder}
import io.fcomb.models.errors.docker.distribution._

object Formats {
  final implicit val encodeDistributionErrorCode: Encoder[DistributionErrorCode] =
    Circe.encoder(DistributionErrorCode)

  final implicit val encodeDistributionError: Encoder[DistributionError] = deriveEncoder

  final implicit val encodeDistributionErrors: Encoder[DistributionErrors] =
    deriveEncoder

  final implicit val decodeDistributionErrorCode: Decoder[DistributionErrorCode] =
    Circe.decoder(DistributionErrorCode)

  final implicit val decodeDistributionError: Decoder[DistributionError] = deriveDecoder

  final implicit val decodeDistributionErrors: Decoder[DistributionErrors] =
    deriveDecoder
}
