/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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
import io.circe.{Decoder, Encoder}
import io.fcomb.models.acl._

object Formats {
  final implicit val encodeAction: Encoder[Action]         = Circe.encoder(Action)
  final implicit val encodeRole: Encoder[Role]             = Circe.encoder(Role)
  final implicit val encodeMemberKind: Encoder[MemberKind] = Circe.encoder(MemberKind)

  final implicit val decodeAction: Decoder[Action]         = Circe.decoder(Action)
  final implicit val decodeRole: Decoder[Role]             = Circe.decoder(Role)
  final implicit val decodeMemberKind: Decoder[MemberKind] = Circe.decoder(MemberKind)
}
