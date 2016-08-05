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
import io.fcomb.models._, EventDetails._

object Formats {
  implicit final val encodeOwnerKind: Encoder[OwnerKind] = Circe.encoder(OwnerKind)
  implicit final val encodeEventKind: Encoder[EventKind] = Circe.encoder(EventKind)

  implicit final val encodeOwner: Encoder[Owner]     = deriveEncoder
  implicit final val encodeSession: Encoder[Session] = deriveEncoder
  implicit final def encodePaginationData[T](
      implicit encoder: Encoder[T]): Encoder[PaginationData[T]] =
    deriveEncoder
  implicit final val encodeCreateRepo: Encoder[CreateRepo] = deriveEncoder
  implicit final val encodePushRepo: Encoder[PushRepo]     = deriveEncoder

  implicit final val encodeEventDetails = new Encoder[EventDetails] {
    def apply(details: EventDetails) = details match {
      case evt: CreateRepo => Encoder[CreateRepo].apply(evt)
      case evt: PushRepo   => Encoder[PushRepo].apply(evt)
    }
  }

  implicit final val encodeEventResponse: Encoder[EventResponse] = deriveEncoder

  implicit final val decodeOwnerKind: Decoder[OwnerKind] = Circe.decoder(OwnerKind)
  implicit final val decodeEventKind: Decoder[EventKind] = Circe.decoder(EventKind)

  implicit final val decodeOwner: Decoder[Owner]     = deriveDecoder
  implicit final val decodeSession: Decoder[Session] = deriveDecoder
  implicit final def decodePaginationData[T](
      implicit decoder: Decoder[T]): Decoder[PaginationData[T]] =
    deriveDecoder
  implicit final val decodeCreateRepo: Decoder[CreateRepo] = deriveDecoder
  implicit final val decodePushRepo: Decoder[PushRepo]     = deriveDecoder

  implicit final val decodeEventDetails: Decoder[EventDetails] = Decoder.instance { c =>
    c.get[EventKind]("kind").flatMap {
      case EventKind.CreateRepo => Decoder[CreateRepo].apply(c)
      case EventKind.PushRepo   => Decoder[PushRepo].apply(c)
    }
  }

  implicit final val decodeEventResponse: Decoder[EventResponse] = deriveDecoder
}
