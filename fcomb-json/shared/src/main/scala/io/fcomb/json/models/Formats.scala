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
import io.circe.{Decoder, Encoder}
import io.fcomb.models._, EventDetails._

object Formats {
  final implicit val encodeOwnerKind: Encoder[OwnerKind] = Circe.encoder(OwnerKind)
  final implicit val encodeEventKind: Encoder[EventKind] = Circe.encoder(EventKind)
  final implicit val encodeUserRole: Encoder[UserRole]   = Circe.encoder(UserRole)

  final implicit val encodeOwner: Encoder[Owner]     = deriveEncoder
  final implicit val encodeSession: Encoder[Session] = deriveEncoder
  final implicit def encodePaginationData[T](
      implicit encoder: Encoder[T]): Encoder[PaginationData[T]] =
    deriveEncoder
  final implicit val encodeCreateRepo: Encoder[CreateRepo] = deriveEncoder
  final implicit val encodePushRepo: Encoder[PushRepo]     = deriveEncoder

  final implicit val encodeEventDetails = new Encoder[EventDetails] {
    def apply(details: EventDetails) = details match {
      case evt: CreateRepo => Encoder[CreateRepo].apply(evt)
      case evt: PushRepo   => Encoder[PushRepo].apply(evt)
    }
  }

  final implicit val encodeEventResponse: Encoder[EventResponse]            = deriveEncoder
  final implicit val encodeSessionPayloadUser: Encoder[SessionPayload.User] = deriveEncoder

  final implicit val decodeOwnerKind: Decoder[OwnerKind] = Circe.decoder(OwnerKind)
  final implicit val decodeEventKind: Decoder[EventKind] = Circe.decoder(EventKind)

  final implicit val decodeOwner: Decoder[Owner]     = deriveDecoder
  final implicit val decodeSession: Decoder[Session] = deriveDecoder
  final implicit def decodePaginationData[T](
      implicit decoder: Decoder[T]): Decoder[PaginationData[T]] =
    deriveDecoder
  final implicit val decodeCreateRepo: Decoder[CreateRepo] = deriveDecoder
  final implicit val decodePushRepo: Decoder[PushRepo]     = deriveDecoder

  final implicit val decodeEventDetails: Decoder[EventDetails] = Decoder.instance { c =>
    c.get[EventKind]("kind").flatMap {
      case EventKind.CreateRepo => Decoder[CreateRepo].apply(c)
      case EventKind.PushRepo   => Decoder[PushRepo].apply(c)
    }
  }

  final implicit val decodeEventResponse: Decoder[EventResponse]            = deriveDecoder
  final implicit val decodeSessionPayloadUser: Decoder[SessionPayload.User] = deriveDecoder
}
