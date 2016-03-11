package io.fcomb.tests

import io.fcomb.Db
import io.fcomb.{persist ⇒ P}
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.slf4j.LoggerFactory

private[this] final object PersistSpec {
  lazy val logger = LoggerFactory.getLogger(getClass)

  lazy val initDbOnlyOnce = {
    logger.debug("Clean db schema")
    val conn = Db.db.createSession().conn
    val st = conn.prepareStatement("""
      DROP SCHEMA IF EXISTS public CASCADE;
      CREATE SCHEMA public;
    """)
    try st.executeUpdate()
    finally conn.close()
    logger.debug("Migrate db schema")
    Await.result(Db.migrate(), Duration.Inf)
  }

  val tables = Set(
    P.docker.distribution.Image.table,
    P.User.table
  )

  val truncateQuery =
    tables
      .map(t ⇒ s"TRUNCATE ${t.baseTableRow.tableName} CASCADE")
      .mkString(";\n")
}

trait PersistSpec extends BeforeAndAfterAll with BeforeAndAfterEach { this: Suite ⇒
  lazy val logger = LoggerFactory.getLogger(getClass)

  override def beforeAll(): Unit =
    PersistSpec.initDbOnlyOnce

  override def beforeEach(): Unit = {
    logger.debug("Truncate tables")
    val conn = Db.db.createSession().conn
    val st = conn.prepareStatement(PersistSpec.truncateQuery)
    try st.executeUpdate()
    finally conn.close()
  }
}
