package io.fcomb

import akka.actor.ActorSystem
import io.fcomb.db.Migration
import io.fcomb.utils.{Config, Implicits}
import io.fcomb.RichPostgresDriver.api.Database
import redis.RedisClient
import scala.concurrent.{ Await, ExecutionContext, Future, blocking }
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource
import com.github.kxbmap.configs.syntax._

object Db {
  private val dbUrl = Config.jdbcConfig.getString("url")
  private val dbUser = Config.jdbcConfig.getString("user")
  private val dbPassword = Config.jdbcConfig.getString("password")

  private val dataSource: DataSource = {
    val ds = new HikariDataSource()
    ds.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource")
    ds.addDataSourceProperty("url", dbUrl)
    ds.addDataSourceProperty("user", dbUser)
    ds.addDataSourceProperty("password", dbPassword)
    ds.setMaximumPoolSize(Config.jdbcConfig.getInt("max-pool-size"))
    ds
  }

  val db = Database.forDataSource(dataSource)

  def migrate()(implicit ec: ExecutionContext) =
    Migration.run(dbUrl, dbUser, dbPassword)

  lazy val redis = RedisClient(
    host = Config.redis.get[String]("host"),
    port = Config.redis.get[Int]("port"),
    db = Config.redis.get[Option[Int]]("db"),
    password = Config.redis.get[Option[String]]("password")
  )(Implicits.global.system)
}
