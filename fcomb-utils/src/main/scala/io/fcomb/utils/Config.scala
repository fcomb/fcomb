package io.fcomb.utils

import com.typesafe.config.ConfigFactory

object Config {
  val config = ConfigFactory.load().getConfig("fcomb-server")

  val actorSystemName = config.getString("actor-system-name")

  val jdbcConfig = config.getConfig("jdbc")

  val jdbcConfigSlick = config.getConfig("jdbc-slick")

  object docker {
    object distribution {
      val imageStorage = config.getString("docker.distribution.rest-api")
    }
  }

  val redis = config.getConfig("redis")

  val mandrillKey = config.getString("mandrill.key")

  case class CertificateIssuer(
    organizationalUnit: String,
    organization:       String,
    city:               String,
    state:              String,
    country:            String
  )

  val certificateIssuer = CertificateIssuer(
    organizationalUnit = config.getString("certificates.issuer.organizationalUnit"),
    organization = config.getString("certificates.issuer.organization"),
    city = config.getString("certificates.issuer.city"),
    state = config.getString("certificates.issuer.state"),
    country = config.getString("certificates.issuer.country")
  )
}
