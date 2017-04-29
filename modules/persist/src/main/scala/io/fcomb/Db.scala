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

package io.fcomb

import com.typesafe.config.ConfigFactory
import io.fcomb.config.JdbcSettings
import io.fcomb.db.Migration
import io.fcomb.PostgresProfile.api.Database
import scala.concurrent.ExecutionContext

object Db {
  def apply(settings: JdbcSettings): Database = {
    val config = ConfigFactory.parseString(s"""
      {
        maxConnections = 50
        numThreads = 25
        url = "${settings.url}"
        user = "${settings.user}"
        password = "${settings.password.value}"
      }""")
    Database.forConfig("", config)
  }

  def migrate(settings: JdbcSettings)(implicit ec: ExecutionContext) =
    Migration.run(settings.url, settings.user, settings.password.value)
}
