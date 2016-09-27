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

package io.fcomb.json.rpc

import cats.data.Xor
import io.circe.generic.semiauto._
import io.circe.{Decoder, DecodingFailure, Encoder}
import io.fcomb.json.models.acl.Formats._
import io.fcomb.rpc._

object Formats {
  final implicit val encodeSessionCreateRequest: Encoder[SessionCreateRequest] = deriveEncoder
  final implicit val encodeUserProfileResponse: Encoder[UserProfileResponse]   = deriveEncoder
  final implicit val encodeUserSignUpRequest: Encoder[UserSignUpRequest]       = deriveEncoder
  final implicit val encodeUserUpdateRequest: Encoder[UserUpdateRequest]       = deriveEncoder
  final implicit val encodeOrganizationCreateRequest: Encoder[OrganizationCreateRequest] =
    deriveEncoder
  // final implicit val encodeOrganizationUpdateRequest: Encoder[OrganizationUpdateRequest] =
  //   deriveEncoder
  final implicit val encodeOrganizationResponse: Encoder[OrganizationResponse] = deriveEncoder
  final implicit val encodeOrganizationGroupRequest: Encoder[OrganizationGroupRequest] =
    deriveEncoder
  final implicit val encodeOrganizationGroupResponse: Encoder[OrganizationGroupResponse] =
    deriveEncoder
  final implicit val encodeMemberUserIdRequest: Encoder[MemberUserIdRequest]     = deriveEncoder
  final implicit val encodeMemberUsernameRequest: Encoder[MemberUsernameRequest] = deriveEncoder
  final implicit val encodeMemberUserRequest = new Encoder[MemberUserRequest] {
    def apply(req: MemberUserRequest) = req match {
      case r: MemberUserIdRequest   => encodeMemberUserIdRequest.apply(r)
      case r: MemberUsernameRequest => encodeMemberUsernameRequest.apply(r)
    }
  }
  final implicit def encodeDataResponse[T](
      implicit encoder: Encoder[T]): Encoder[DataResponse[T]] =
    deriveEncoder

  final implicit val decodeSessionCreateRequest: Decoder[SessionCreateRequest] = deriveDecoder
  final implicit val decodeUserProfileResponse: Decoder[UserProfileResponse]   = deriveDecoder
  final implicit val decodeUserSignUpRequest: Decoder[UserSignUpRequest]       = deriveDecoder
  final implicit val decodeUserUpdateRequest: Decoder[UserUpdateRequest]       = deriveDecoder
  final implicit val decodeOrganizationCreateRequest: Decoder[OrganizationCreateRequest] =
    deriveDecoder
  // final implicit val decodeOrganizationOrganizationUpdateRequest: Decoder[
  //   OrganizationUpdateRequest]                                                 = deriveDecoder
  final implicit val decodeOrganizationResponse: Decoder[OrganizationResponse] = deriveDecoder
  final implicit val decodeOrganizationGroupRequest: Decoder[OrganizationGroupRequest] =
    deriveDecoder
  final implicit val decodeOrganizationGroupResponse: Decoder[OrganizationGroupResponse] =
    deriveDecoder
  final implicit val decodeMemberUserRequest: Decoder[MemberUserRequest] = Decoder.instance { c =>
    val id       = c.downField("id")
    val username = c.downField("username")
    if (id.succeeded && !username.succeeded) {
      Decoder[Int].apply(id.any).map(MemberUserIdRequest)
    } else if (!id.succeeded && username.succeeded) {
      Decoder[String].apply(username.any).map(MemberUsernameRequest)
    } else Xor.Left(DecodingFailure("You should pass 'id' or 'username' field", c.history))
  }
  final implicit def decodeDataResponse[T](
      implicit decoder: Decoder[T]): Decoder[DataResponse[T]] =
    deriveDecoder
}
