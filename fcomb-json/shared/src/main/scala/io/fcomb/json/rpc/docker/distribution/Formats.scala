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

package io.fcomb.json.rpc.docker.distribution

import io.circe.generic.semiauto._
import io.circe.{Encoder, Decoder}
import io.fcomb.rpc.docker.distribution._
import io.fcomb.json.models.Formats._
import io.fcomb.json.models.acl.Formats._
import io.fcomb.json.models.docker.distribution.Formats._

object Formats {
  implicit final val encodeRepositoryResponse: Encoder[RepositoryResponse]       = deriveEncoder
  implicit final val encodeRepositoryTagResponse: Encoder[RepositoryTagResponse] = deriveEncoder
  implicit final val encodeImageCreateRequest: Encoder[ImageCreateRequest]       = deriveEncoder
  implicit final val encodeImageUpdateRequest: Encoder[ImageUpdateRequest]       = deriveEncoder
  implicit final val encodeImageWebhookRequest: Encoder[ImageWebhookRequest]     = deriveEncoder
  implicit final val encodeImageWebhookResponse: Encoder[ImageWebhookResponse]   = deriveEncoder

  implicit final val decodeRepositoryResponse: Decoder[RepositoryResponse]       = deriveDecoder
  implicit final val decodeRepositoryTagResponse: Decoder[RepositoryTagResponse] = deriveDecoder
  implicit final val decodeImageCreateRequest: Decoder[ImageCreateRequest]       = deriveDecoder
  implicit final val decodeImageUpdateRequest: Decoder[ImageUpdateRequest]       = deriveDecoder
  implicit final val decodeImageWebhookRequest: Decoder[ImageWebhookRequest]     = deriveDecoder
  implicit final val decodeImageWebhookResponse: Decoder[ImageWebhookResponse]   = deriveDecoder
}
