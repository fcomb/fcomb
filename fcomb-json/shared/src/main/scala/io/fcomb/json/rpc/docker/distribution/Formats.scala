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
import io.circe.{Decoder, Encoder}
import io.fcomb.rpc.docker.distribution._
import io.fcomb.json.models.Formats._
import io.fcomb.json.models.acl.Formats._
import io.fcomb.json.models.docker.distribution.Formats._

object Formats {
  final implicit val encodeRepositoryResponse: Encoder[RepositoryResponse]       = deriveEncoder
  final implicit val encodeRepositoryTagResponse: Encoder[RepositoryTagResponse] = deriveEncoder
  final implicit val encodeImageCreateRequest: Encoder[ImageCreateRequest]       = deriveEncoder
  final implicit val encodeImageUpdateRequest: Encoder[ImageUpdateRequest]       = deriveEncoder
  final implicit val encodeImageWebhookRequest: Encoder[ImageWebhookRequest]     = deriveEncoder
  final implicit val encodeImageWebhookResponse: Encoder[ImageWebhookResponse]   = deriveEncoder

  final implicit val decodeRepositoryResponse: Decoder[RepositoryResponse]       = deriveDecoder
  final implicit val decodeRepositoryTagResponse: Decoder[RepositoryTagResponse] = deriveDecoder
  final implicit val decodeImageCreateRequest: Decoder[ImageCreateRequest]       = deriveDecoder
  final implicit val decodeImageUpdateRequest: Decoder[ImageUpdateRequest]       = deriveDecoder
  final implicit val decodeImageWebhookRequest: Decoder[ImageWebhookRequest]     = deriveDecoder
  final implicit val decodeImageWebhookResponse: Decoder[ImageWebhookResponse]   = deriveDecoder
}
