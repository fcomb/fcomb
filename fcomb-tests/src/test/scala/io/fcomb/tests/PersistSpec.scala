/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.tests

import io.fcomb.Db
import io.fcomb.persist.UsersRepo
import io.fcomb.persist.docker.distribution._
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}

private[this] object PersistSpec extends LazyLogging {
  lazy val initDbOnlyOnce = {
    logger.info("Clean db schema")
    val conn = Db.db.createSession().conn
    val st   = conn.prepareStatement("""
      DROP SCHEMA IF EXISTS public CASCADE;
      CREATE SCHEMA public;
    """)
    try st.executeUpdate()
    finally conn.close()
    logger.info("Migrate db schema")
    Await.result(Db.migrate(), 30.seconds)
  }

  val tables = Set(
    ImageManifestsRepo.table,
    ImageBlobsRepo.table,
    ImagesRepo.table,
    UsersRepo.table
  )

  val truncateQuery =
    tables.map(t => s"TRUNCATE ${t.baseTableRow.tableName} CASCADE").mkString(";\n")
}

trait PersistSpec extends BeforeAndAfterAll with BeforeAndAfterEach with LazyLogging {
  this: Suite =>
  override def beforeAll(): Unit = {
    super.beforeAll()
    PersistSpec.initDbOnlyOnce
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    logger.debug("Truncate tables")
    val conn = Db.db.createSession().conn
    val st   = conn.prepareStatement(PersistSpec.truncateQuery)
    try st.executeUpdate()
    finally conn.close()
    ()
  }
}
