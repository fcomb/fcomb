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

package io.fcomb

import io.fcomb.db.Migration
import io.fcomb.utils.Config
import io.fcomb.FcombPostgresProfile.api.Database
import scala.concurrent.ExecutionContext

object Db {
  private val dbUrl      = Config.jdbcConfig.getString("url")
  private val dbUser     = Config.jdbcConfig.getString("user")
  private val dbPassword = Config.jdbcConfig.getString("password")

  val db = Database.forConfig("", Config.jdbcConfigSlick)

  def migrate()(implicit ec: ExecutionContext) =
    Migration.run(dbUrl, dbUser, dbPassword)
}
