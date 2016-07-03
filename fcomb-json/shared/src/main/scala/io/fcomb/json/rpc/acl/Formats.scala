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

package io.fcomb.json.rpc.acl

import cats.data.Xor
import io.circe.generic.semiauto._
import io.circe.{Encoder, Decoder, DecodingFailure}
import io.fcomb.json.models.acl.Formats._
import io.fcomb.rpc.acl._

object Formats {
  implicit final val encodePermissionUserMemberResponse: Encoder[PermissionUserMemberResponse] =
    deriveEncoder
  implicit final val encodePermissionResponse: Encoder[PermissionResponse] = deriveEncoder
  implicit final val encodePermissionUserIdRequest: Encoder[PermissionUserIdRequest] =
    deriveEncoder
  implicit final val encodePermissionUsernameRequest: Encoder[PermissionUsernameRequest] =
    deriveEncoder
  implicit final val encodePermissionPermissionUserRequest = new Encoder[PermissionUserRequest] {
    def apply(req: PermissionUserRequest) = req match {
      case r: PermissionUserIdRequest   => encodePermissionUserIdRequest.apply(r)
      case r: PermissionUsernameRequest => encodePermissionUsernameRequest.apply(r)
    }
  }
  implicit final val encodePermissionPermissionUserCreateRequest: Encoder[
    PermissionUserCreateRequest] = deriveEncoder

  implicit final val decodePermissionUserMemberResponse: Decoder[PermissionUserMemberResponse] =
    deriveDecoder
  implicit final val decodePermissionResponse: Decoder[PermissionResponse] = deriveDecoder
  implicit final val decodePermissionPermissionUserRequest: Decoder[PermissionUserRequest] =
    Decoder.instance { c =>
      val id       = c.downField("id")
      val username = c.downField("username")
      if (id.succeeded && !username.succeeded) {
        Decoder[Int].apply(id.any).map(PermissionUserIdRequest)
      } else if (!id.succeeded && username.succeeded) {
        Decoder[String].apply(username.any).map(PermissionUsernameRequest)
      } else Xor.left(DecodingFailure("You should pass 'id' or 'username' field", c.history))
    }
  implicit final val decodePermissionPermissionUserCreateRequest: Decoder[
    PermissionUserCreateRequest] = deriveDecoder
}
