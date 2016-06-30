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

package io.fcomb.json.models.acl

import enumeratum.Circe
import io.circe.{Encoder, Decoder}
import io.fcomb.models.acl._

object Formats {
  implicit final val encodeAction: Encoder[Action]         = Circe.encoder(Action)
  implicit final val encodeMemberKind: Encoder[MemberKind] = Circe.encoder(MemberKind)

  implicit final val decodeAction: Decoder[Action]         = Circe.decoder(Action)
  implicit final val decodeMemberKind: Decoder[MemberKind] = Circe.decoder(MemberKind)
  MemberKind
}
