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

package io.fcomb.tests

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.headers.Authorization
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import io.fcomb.crypto.Jwt
import io.fcomb.models.User
import io.fcomb.utils.Config
import java.time.Instant

object AuthSpec extends RequestBuilding {
  def bearerToken(user: User): String =
    Jwt.encode(user, Config.jwt.secret, Instant.now(), Config.jwt.sessionTtl)

  def authenticate(user: User) =
    addHeader(Authorization(OAuth2BearerToken(bearerToken(user))))
}
