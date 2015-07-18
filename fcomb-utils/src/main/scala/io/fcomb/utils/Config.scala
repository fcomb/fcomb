package io.fcomb.utils

import com.typesafe.config.ConfigFactory

object Config {
  val config = ConfigFactory.load().getConfig("fcomb-server")

  val jdbcConfig = config.getConfig("jdbc")

  val scredis = config.getConfig("scredis")

  val mandrillKey = config.getString("mandrill.key")
}
