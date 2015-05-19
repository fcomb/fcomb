package io.fcomb.utils

import com.typesafe.config.ConfigFactory

object Config {
  val config = ConfigFactory.load()

  val serverConfig = config.getConfig("fcomb-server")

  val jdbcConfig = config.getConfig("jdbc")

  val scredis = config.getConfig("scredis")

  // case class S3Credentials(
  //   accessKeyId:     String,
  //   secretAccessKey: String,
  //   region:          String,
  //   bucketName:      String
  // )

  // val s3Config = config.getConfig("aws.s3")
  // val s3Credentials = S3Credentials(
  //   accessKeyId = s3Config.getString("access-key-id"),
  //   secretAccessKey = s3Config.getString("secret-access-key"),
  //   region = s3Config.getString("region"),
  //   bucketName = s3Config.getString("bucket-name")
  // )

  // val cloudfrontConfig = ConfigFactory.load().getConfig("aws.cloudfront")
  // val cloudfrontPrefix = s"${cloudfrontConfig.getString("schema")}${cloudfrontConfig.getString("domain")}"
}
