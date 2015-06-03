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
  val dataSource: DataSource = {
    val ds = new HikariDataSource()
    ds.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource")
    ds.addDataSourceProperty("url", Config.jdbcConfig.getString("url"))
    ds.addDataSourceProperty("user", Config.jdbcConfig.getString("user"))
    ds.addDataSourceProperty("password", Config.jdbcConfig.getString("password"))
    ds.setMaximumPoolSize(Config.jdbcConfig.getInt("max-pool-size"))
    ds
  }

  val pool = ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))

  implicit val session = AutoSession

  def migrate(): Unit = {
    val flyway = new Flyway()
    flyway.setDataSource(dataSource)
    flyway.setLocations("sql.migration")
    flyway.migrate()
  }

  val cache = Redis(Config.scredis)
}
