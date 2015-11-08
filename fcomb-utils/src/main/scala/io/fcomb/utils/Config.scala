package io.fcomb.utils

import com.typesafe.config.ConfigFactory

object Config {
  val config = ConfigFactory.load().getConfig("fcomb-server")

  val actorSystemName = config.getString("actor-system-name")

  val jdbcConfig = config.getConfig("jdbc")

  val jdbcConfigSlick = config.getConfig("jdbc-slick")

  val redis = config.getConfig("redis")

  val mandrillKey = config.getString("mandrill.key")
}
