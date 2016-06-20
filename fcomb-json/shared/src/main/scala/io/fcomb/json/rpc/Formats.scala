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

import io.circe.generic.auto._
import io.circe.{Encoder, Decoder}
import io.fcomb.rpc._
import shapeless.cachedImplicit

object Formats {
  implicit final val encodeSessionCreateRequest: Encoder[SessionCreateRequest] = cachedImplicit
  implicit final val encodeUserProfileResponse: Encoder[UserProfileResponse]   = cachedImplicit
  implicit final val encodeUserSignUpRequest: Encoder[UserSignUpRequest]       = cachedImplicit
  implicit final val encodeUserUpdateRequest: Encoder[UserUpdateRequest]       = cachedImplicit

  implicit final val decodeSessionCreateRequest: Decoder[SessionCreateRequest] = cachedImplicit
  implicit final val decodeUserProfileResponse: Decoder[UserProfileResponse]   = cachedImplicit
  implicit final val decodeUserSignUpRequest: Decoder[UserSignUpRequest]       = cachedImplicit
  implicit final val decodeUserUpdateRequest: Decoder[UserUpdateRequest]       = cachedImplicit
}
