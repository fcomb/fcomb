package io.fcomb.tests

import io.fcomb.Db
import io.fcomb.persist.UsersRepo
import io.fcomb.persist.docker.distribution._
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.slf4j.LoggerFactory

private[this] object PersistSpec {
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
    Await.result(Db.migrate(), 30.seconds)
  }

  val tables = Set(
    ImageManifestsRepo.table,
    ImageBlobsRepo.table,
    ImagesRepo.table,
    UsersRepo.table
  )

  val truncateQuery =
    tables
      .map(t ⇒ s"TRUNCATE ${t.baseTableRow.tableName} CASCADE")
      .mkString(";\n")
}

trait PersistSpec extends BeforeAndAfterAll with BeforeAndAfterEach { this: Suite ⇒
  lazy val logger = LoggerFactory.getLogger(getClass)

  override def beforeAll(): Unit = {
    super.beforeAll()
    PersistSpec.initDbOnlyOnce
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    logger.debug("Truncate tables")
    val conn = Db.db.createSession().conn
    val st = conn.prepareStatement(PersistSpec.truncateQuery)
    try st.executeUpdate()
    finally conn.close()
  }
}
