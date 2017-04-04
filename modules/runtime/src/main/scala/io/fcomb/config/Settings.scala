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

package io.fcomb.config

import scala.concurrent.duration.FiniteDuration

@specialized
final case class Hidden[+T](val value: T) {
  final override def toString(): String = "..."
}

final case class ApiSettings(
    interface: String,
    httpPort: Int,
    httpsPort: Int
)

final case class StorageSettings(
    path: String
)

final case class JdbcSettings(
    url: String,
    user: String,
    password: Hidden[String]
)

final case class GcSettings(
    outdatedPeriod: FiniteDuration,
    outdatedCheckInterval: FiniteDuration,
    deletingCheckInterval: FiniteDuration
)

final case class SecuritySettings(
    realm: String,
    openSignUp: Boolean,
    anonymousPublicRepositories: Boolean
)

final case class JwtSettings(
    secret: Hidden[String],
    sessionTtl: FiniteDuration,
    resetPasswordTtl: FiniteDuration
)

final case class Settings(
    api: ApiSettings,
    storage: StorageSettings,
    jdbc: JdbcSettings,
    gc: GcSettings,
    security: SecuritySettings,
    jwt: JwtSettings
)
