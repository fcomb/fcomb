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

package io.fcomb.rpc.helpers

import io.fcomb.models.User
import io.fcomb.rpc.{UserProfileResponse, UserResponse}

object UserHelpers {
  def response(user: User): UserResponse =
    UserResponse(
      id = user.getId(),
      email = user.email,
      username = user.username,
      fullName = user.fullName,
      role = user.role
    )

  def profileResponse(user: User): UserProfileResponse =
    UserProfileResponse(
      id = user.getId(),
      username = user.username,
      fullName = user.fullName
    )
}
