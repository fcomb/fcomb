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

package io.fcomb.models

import com.github.t3hnar.bcrypt._
import java.time.ZonedDateTime

final case class User(
    id: Option[Long] = None,
    email: String,
    username: String,
    fullName: Option[String],
    passwordHash: String,
    createdAt: ZonedDateTime,
    updatedAt: Option[ZonedDateTime]
)
    extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))

  def isValidPassword(password: String) =
    password.isBcrypted(passwordHash)

  def toProfile =
    UserProfileResponse(
      id = id,
      email = email,
      username = username,
      fullName = fullName,
      createdAt = createdAt,
      updatedAt = updatedAt
    )
}

final case class UserProfileResponse(
    id: Option[Long] = None,
    email: String,
    username: String,
    fullName: Option[String],
    createdAt: ZonedDateTime,
    updatedAt: Option[ZonedDateTime]
)

final case class UserSignUpRequest(
    email: String,
    password: String,
    username: String,
    fullName: Option[String]
)

final case class UserUpdateRequest(
    email: String,
    username: String,
    fullName: Option[String]
)
