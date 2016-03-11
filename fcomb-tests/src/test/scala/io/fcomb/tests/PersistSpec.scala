package io.fcomb.tests

import io.fcomb.Db
import io.fcomb.persist._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.scalatest._ // {BeforeAndAfterAll, BeforeAndAfterEach}
import org.slf4j.LoggerFactory

private[this] final object PersistSpec {
  lazy val logger = LoggerFactory.getLogger(getClass)

  lazy val initDbOnce = {
    logger.debug("Clean db schema")
    val conn = Db.db.createSession().conn
    val st = conn.prepareStatement("""
      DROP SCHEMA IF EXISTS public CASCADE;
      CREATE SCHEMA public;
    """)
    st.executeUpdate()
    conn.close()
    logger.debug("Migrate db schema")
    Await.result(Db.migrate(), Duration.Inf)
  }
}

trait PersistSpec extends BeforeAndAfterAll with BeforeAndAfterEach { this: Suite ⇒
  lazy val logger = LoggerFactory.getLogger(getClass)

  override def beforeAll(): Unit =
    PersistSpec.initDbOnce

  override def beforeEach(): Unit = {
    logger.debug("Truncate tables")
    val conn = Db.db.createSession().conn
    val tables = Set(
      docker.distribution.Image.table,
      User.table
    )
    val st = conn.prepareStatement(tables.map { table ⇒
      s"TRUNCATE ${table.baseTableRow.tableName} CASCADE"
    }.mkString(";\n"))
    st.executeUpdate()
    conn.close()
  }
}
