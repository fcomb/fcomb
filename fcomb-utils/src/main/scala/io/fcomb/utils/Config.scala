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

package io.fcomb.utils

import com.typesafe.config.ConfigFactory

object Config {
  val config          = ConfigFactory.load().getConfig("fcomb-server")
  val actorSystemName = config.getString("actor-system-name")
  val jdbcConfig      = config.getConfig("jdbc")
  val jdbcConfigSlick = config.getConfig("jdbc-slick")

  object docker {
    object distribution {
      val imageStorage = config.getString("docker.distribution.image-storage")
      val realm        = config.getString("docker.distribution.realm")
    }
  }

  val redis       = config.getConfig("redis")
  val mandrillKey = config.getString("mandrill.key")
}
