package io.fcomb

import io.fcomb.utils.Config
import scalikejdbc._
import org.flywaydb.core.Flyway
import scredis.Redis
import scala.concurrent.{ Await, ExecutionContext, Future, blocking }
import scala.concurrent.duration._

object Db {
  Class.forName("org.postgresql.Driver")

  def migrate(): Unit = {
    val flyway = new Flyway()
    flyway.setDataSource(
      Config.jdbcConfig.getString("url"),
      Config.jdbcConfig.getString("user"),
      Config.jdbcConfig.getString("password")
    )
    flyway.setLocations("sql.migration")
    flyway.migrate()
  }

  val pool = ConnectionPool.singleton(
    url = Config.jdbcConfig.getString("url"),
    user = Config.jdbcConfig.getString("user"),
    password = Config.jdbcConfig.getString("password")
  )

  implicit val session = AutoSession

  val cache = Redis(Config.scredis)
}
