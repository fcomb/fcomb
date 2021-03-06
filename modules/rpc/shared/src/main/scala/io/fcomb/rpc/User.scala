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

package io.fcomb.rpc

import io.fcomb.models.UserRole

final case class UserSignUpRequest(
    email: String,
    password: String,
    username: String,
    fullName: Option[String]
)

final case class UserResponse(
    id: Int,
    email: String,
    username: String,
    fullName: Option[String],
    role: UserRole
) extends ResponseModelWithIntPk {
  def title = username + fullName.map(n => s" ($n)").getOrElse("")
}

final case class UserProfileResponse(
    id: Int,
    username: String,
    fullName: Option[String]
) {
  def title = username + fullName.map(n => s" ($n)").getOrElse("")
}

final case class UserCreateRequest(
    email: String,
    password: String,
    username: String,
    fullName: Option[String],
    role: UserRole
)

final case class UserUpdateRequest(
    email: String,
    password: Option[String],
    fullName: Option[String],
    role: UserRole
)
