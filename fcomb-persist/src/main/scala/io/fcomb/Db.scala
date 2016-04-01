package io.fcomb

import io.fcomb.db.Migration
import io.fcomb.utils.{Config, Implicits}
import io.fcomb.RichPostgresDriver.api.Database
import redis.RedisClient
import scala.concurrent.ExecutionContext
import configs.syntax._

object Db {
  private val dbUrl = Config.jdbcConfig.getString("url")
  private val dbUser = Config.jdbcConfig.getString("user")
  private val dbPassword = Config.jdbcConfig.getString("password")

  val db = Database.forConfig("", Config.jdbcConfigSlick)

  def migrate()(implicit ec: ExecutionContext) =
    Migration.run(dbUrl, dbUser, dbPassword)

  lazy val redis = RedisClient(
    host = Config.redis.get[String]("host").value,
    port = Config.redis.get[Int]("port").value,
    db = Config.redis.get[Option[Int]]("db").value,
    password = Config.redis.get[Option[String]]("password").value
  )(Implicits.global.system)
}
