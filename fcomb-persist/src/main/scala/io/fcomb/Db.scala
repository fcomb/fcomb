package io.fcomb

import io.fcomb.utils.Config
import scalikejdbc._
import org.flywaydb.core.Flyway
import scredis.Redis
import scala.concurrent.{ Await, ExecutionContext, Future, blocking }
import scala.concurrent.duration._
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object Db {
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

  val dataSource: DataSource = {
    val ds = new HikariDataSource()
    ds.setDataSourceClassName("org.postgresql.Driver")
    ds.addDataSourceProperty("url", Config.jdbcConfig.getString("url"))
    ds.addDataSourceProperty("user", Config.jdbcConfig.getString("user"))
    ds.addDataSourceProperty("password", Config.jdbcConfig.getString("password"))
    ds
  }

  val pool = ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))

  implicit val session = AutoSession

  val cache = Redis(Config.scredis)
}
