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

import com.typesafe.config.{Config, ConfigFactory, ConfigValue}
import com.typesafe.scalalogging.LazyLogging
import pureconfig.error.ConfigReaderFailures
import pureconfig.{loadConfig => pureLoadConfig, _}

object Configuration extends LazyLogging {
  def loadConfig(defaults: Config = ConfigFactory.empty()): Config =
    defaults
      .withFallback(ConfigFactory.parseString(defaultConfig))
      .withFallback(ConfigFactory.load())
      .resolve()

  def loadSettings(config: Config = loadConfig()): Either[ConfigReaderFailures, Settings] =
    pureLoadConfig[Settings](config, "fcomb")

  private val defaultConfig = s"""
                                 |akka {
                                 |  logger-startup-timeout = 10s
                                 |  log-dead-letters-during-shutdown = off
                                 |  loglevel = INFO
                                 |  loggers = ["akka.event.slf4j.Slf4jLogger"]
                                 |  logging-filter = "akka.event.slf4j.Slf4jLoggingFilter"
                                 |
                                 |  actor.provider = "akka.cluster.ClusterActorRefProvider"
                                 |
                                 |  http {
                                 |    server {
                                 |      server-header = fcomb
                                 |      transparent-head-requests = off
                                 |      parsing.max-content-length = infinite
                                 |    }
                                 |
                                 |    client.user-agent-header = "fcomb"
                                 |  }
                                 |
                                 |  cluster.sharding.state-store-mode = "ddata"
                                 |}
                                 |
                                 |fcomb {
                                 |  api {
                                 |    interface = "0.0.0.0"
                                 |    http-port = 8080
                                 |    https-port = 8443
                                 |  }
                                 |
                                 |  storage.path = "/data"
                                 |
                                 |  gc {
                                 |    outdated-period = 1d
                                 |    outdated-check-interval = 1h
                                 |    deleting-check-interval = 10m
                                 |  }
                                 |
                                 |  jdbc {
                                 |    url = "jdbc:postgresql://127.0.0.1:5432/fcomb"
                                 |    user = postgres
                                 |    password = ""
                                 |  }
                                 |
                                 |  jwt {
                                 |    secret = ""
                                 |    sessionTtl = 30d
                                 |    resetPasswordTtl = 1h
                                 |  }
                                 |
                                 |  security {
                                 |    realm = "fcomb registry"
                                 |    open-sign-up = false
                                 |    anonymous-public-repositories = false
                                 |  }
                                 |}
    """.stripMargin

  implicit def hiddenConfigReader[T](implicit cr: ConfigReader[T]): ConfigReader[Hidden[T]] =
    new ConfigReader[Hidden[T]] {
      override def from(config: ConfigValue): Either[ConfigReaderFailures, Hidden[T]] =
        cr.from(config).map(Hidden(_))
    }
}
