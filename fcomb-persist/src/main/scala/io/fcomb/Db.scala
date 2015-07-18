package io.fcomb

import io.fcomb.utils.Config
import io.fcomb.RichPostgresDriver.api.Database
// import scalikejdbc._
import org.flywaydb.core.Flyway
import scredis.Redis
import scala.concurrent.{ Await, ExecutionContext, Future, blocking }
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

  // ConnectionPool.singleton(new DataSourceConnectionPool(dataSource))

  // implicit val session = AutoSession

  lazy val db = Database.forConfig("", Config.jdbcConfig)

  def migrate()(implicit ec: ExecutionContext) = Future {
    blocking {
      val flyway = new Flyway()
      flyway.setDataSource(
        Config.jdbcConfig.getString("url"),
        Config.jdbcConfig.getString("user"),
        Config.jdbcConfig.getString("password")
      )
      flyway.setLocations("sql.migration")
      flyway.migrate()
    }
  }

  lazy val cache = Redis(Config.scredis)
}
