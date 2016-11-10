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

import cats.syntax.either._
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.fcomb.json.models.acl.Formats._
import io.fcomb.models.acl.MemberKind
import io.fcomb.rpc.acl._
import scala.util.Left

object Formats {
  final implicit val encodePermissionUserMemberResponse: Encoder[PermissionUserMemberResponse] =
    Encoder.forProduct5("id", "kind", "isOwner", "username", "fullName")(r =>
      (r.id, r.kind: MemberKind, r.isOwner, r.username, r.fullName))
  final implicit val encodePermissionGroupMemberResponse: Encoder[PermissionGroupMemberResponse] =
    Encoder.forProduct3("id", "kind", "name")(r => (r.id, r.kind: MemberKind, r.name))
  final implicit val encodePermissionMemberResponse: Encoder[PermissionMemberResponse] =
    new Encoder[PermissionMemberResponse] {
      def apply(res: PermissionMemberResponse) = res match {
        case r: PermissionUserMemberResponse  => encodePermissionUserMemberResponse.apply(r)
        case r: PermissionGroupMemberResponse => encodePermissionGroupMemberResponse.apply(r)
      }
    }
  final implicit val encodePermissionResponse: Encoder[PermissionResponse] = deriveEncoder
  private final val encodePermissionUserIdRequest: Encoder[PermissionUserIdRequest] =
    Encoder.forProduct2("id", "kind")(r => (r.id, r.kind: MemberKind))
  private final val encodePermissionUsernameRequest: Encoder[PermissionUsernameRequest] =
    Encoder.forProduct2("username", "kind")(r => (r.username, r.kind: MemberKind))
  private final val encodePermissionGroupIdRequest: Encoder[PermissionGroupIdRequest] =
    Encoder.forProduct2("id", "kind")(r => (r.id, r.kind: MemberKind))
  private final val encodePermissionGroupNameRequest: Encoder[PermissionGroupNameRequest] =
    Encoder.forProduct2("name", "kind")(r => (r.name, r.kind: MemberKind))
  final implicit val encodePermissionMemberRequest = new Encoder[PermissionMemberRequest] {
    def apply(req: PermissionMemberRequest) = req match {
      case r: PermissionUserIdRequest    => encodePermissionUserIdRequest.apply(r)
      case r: PermissionUsernameRequest  => encodePermissionUsernameRequest.apply(r)
      case r: PermissionGroupIdRequest   => encodePermissionGroupIdRequest.apply(r)
      case r: PermissionGroupNameRequest => encodePermissionGroupNameRequest.apply(r)
    }
  }
  final implicit val encodePermissionPermissionCreateRequest: Encoder[PermissionCreateRequest] =
    deriveEncoder

  final implicit val decodePermissionUserMemberResponse: Decoder[PermissionUserMemberResponse] =
    deriveDecoder
  final implicit val decodePermissionGroupMemberResponse: Decoder[PermissionGroupMemberResponse] =
    deriveDecoder
  final implicit val decodePermissionMemberResponse: Decoder[PermissionMemberResponse] =
    Decoder.instance { c =>
      c.get[MemberKind]("kind").flatMap {
        case MemberKind.User  => decodePermissionUserMemberResponse.apply(c)
        case MemberKind.Group => decodePermissionGroupMemberResponse.apply(c)
      }
    }

  final implicit val decodePermissionResponse: Decoder[PermissionResponse] = deriveDecoder
  final implicit val decodePermissionMemberRequest: Decoder[PermissionMemberRequest] =
    Decoder.instance { c =>
      val id = c.downField("id")
      c.get[MemberKind]("kind").flatMap {
        case MemberKind.User =>
          val username = c.downField("username")
          if (id.succeeded && !username.succeeded)
            Decoder[Int].apply(id.any).map(PermissionUserIdRequest)
          else if (!id.succeeded && username.succeeded)
            Decoder[String].apply(username.any).map(PermissionUsernameRequest)
          else Left(DecodingFailure("You should pass 'id' or 'username' field", c.history))
        case MemberKind.Group =>
          val name = c.downField("name")
          if (id.succeeded && !name.succeeded)
            Decoder[Int].apply(id.any).map(PermissionGroupIdRequest)
          else if (!id.succeeded && name.succeeded)
            Decoder[String].apply(name.any).map(PermissionGroupNameRequest)
          else Left(DecodingFailure("You should pass 'id' or 'name' field", c.history))
      }
    }
  final implicit val decodePermissionPermissionCreateRequest: Decoder[PermissionCreateRequest] =
    deriveDecoder
}
