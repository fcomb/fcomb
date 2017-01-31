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

package io.fcomb.config

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.fcomb.models._
import scala.concurrent.ExecutionContext
import scala.util.Try

object Configuration extends LazyLogging {
  def loadConfig(defaults: Config = ConfigFactory.empty()): Config =
    defaults
      .withFallback(ConfigFactory.parseString(defaultConfig))
      .withFallback(ConfigFactory.load())
      .resolve()

  def loadSettings(config: Config = loadConfig()): Settings = {
    val fConf = config.getConfig("fcomb")
    val api = ApiSettings(
      interface = Try(fConf.getString("api.interface")).getOrElse("0.0.0.0"),
      httpPort = Try(fConf.getInt("api.http-port")).getOrElse(8080),
      httpsPort = Try(fConf.getInt("api.https-port")).getOrElse(8443)
    )
    final case class StorageSettings(
        path: String
    )
    final case class JdbcSettings(
        url: String,
        user: String,
        password: String
    )
    final case class GcSettings(
        outdatedPeriod: JDuration,
        outdatedCheckInterval: FiniteDuration,
        deletingCheckInterval: FiniteDuration
    )
    final case class SecuritySettings(
        realm: String,
        isOpenSignUp: Boolean,
        isAnonymousPublicRepositories: Boolean
    )
    final case class JwtSettings(
        secret: String,
        sessionTtl: JDuration,
        resetPasswordTtl: JDuration
    )
    Settings(api, ???, ???, ???, ???, ???)
  }

  private val defaultConfig = s"""
                                 |akka {
                                 |  logger-startup-timeout: 10s
                                 |  log-dead-letters-during-shutdown: off
                                 |  loglevel: "INFO"
                                 |  loggers: ["akka.event.slf4j.Slf4jLogger"]
                                 |  logging-filter: "akka.event.slf4j.Slf4jLoggingFilter"
                                 |
                                 |  actor.provider: "akka.cluster.ClusterActorRefProvider"
                                 |
                                 |  http {
                                 |    server {
                                 |      server-header = "fcomb"
                                 |      transparent-head-requests = off
                                 |      parsing.max-content-length = infinite
                                 |    }
                                 |
                                 |    client.user-agent-header = "fcomb"
                                 |  }
                                 |
                                 |  cluster.sharding.state-store-mode: "ddata"
                                 |}
                                 |
                                 |jdbc-slick {
                                 |  dataSourceClass = "org.postgresql.ds.PGSimpleDataSource"
                                 |  maxConnections = 50
                                 |  numThreads = 10
                                 |}
    """.stripMargin
}
