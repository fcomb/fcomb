package io.fcomb

import io.fcomb.db.Migration
import io.fcomb.utils.Config
import io.fcomb.RichPostgresDriver.api.Database
import scredis.Redis
import scala.concurrent.{ Await, ExecutionContext, Future, blocking }
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

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

  val cache = Redis(Config.scredis)
}
