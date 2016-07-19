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
  implicit final val encodeDistributionErrorCode: Encoder[DistributionErrorCode] =
    Circe.encoder(DistributionErrorCode)

  implicit final val encodeDistributionError: Encoder[DistributionError] =
    Encoder.forProduct2("code", "message")(e => (e.code.entryName, e.message))

  implicit final val encodeDistributionErrorResponse: Encoder[DistributionErrorResponse] =
    deriveEncoder

  implicit final val decodeDistributionErrorCode: Decoder[DistributionErrorCode] =
    Circe.decoder(DistributionErrorCode)

  implicit final val decodeDistributionError: Decoder[DistributionError] = {
    Decoder.instance { c =>
      for {
        code    <- c.get[DistributionErrorCode]("code")
        message <- c.get[String]("message")
        // detail  <- c.get[Option[DistributionErrorDetail]]("detail")
      } yield {
        code match {
          case DistributionErrorCode.DigestInvalid =>
            DistributionError.DigestInvalid(message)
          case DistributionErrorCode.Unknown =>
            DistributionError.Unknown(message)
          case DistributionErrorCode.NameInvalid =>
            DistributionError.NameInvalid(message)
          case DistributionErrorCode.ManifestInvalid =>
            DistributionError.ManifestInvalid(message)
          case DistributionErrorCode.ManifestUnknown =>
            DistributionError.ManifestUnknown(message)
          case DistributionErrorCode.BlobUploadInvalid =>
            DistributionError.BlobUploadInvalid(message)
          case DistributionErrorCode.NameUnknown =>
            DistributionError.NameUnknown(message)
          case DistributionErrorCode.Unauthorized =>
            DistributionError.Unauthorized(message)
        }
      }
    }
  }

  implicit final val decodeDistributionErrorResponse: Decoder[DistributionErrorResponse] =
    deriveDecoder
}
