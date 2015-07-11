package io.fcomb

import com.github.tminglei.slickpg._

trait RichPostgresDriver extends ExPostgresDriver
                          with PgArraySupport
                          with PgDateSupport
                          with PgJsonSupport
                          with PgNetSupport
                          with PgLTreeSupport
                          with PgRangeSupport
                          with PgHStoreSupport
                          with PgEnumSupport
                          with PgSearchSupport {

  override val pgjson = "jsonb"

  override val api = new API with ArrayImplicits
                             with DateTimeImplicits
                             with SimpleJsonImplicits
                             with NetImplicits
                             with LTreeImplicits
                             with RangeImplicits
                             with HStoreImplicits
                             with SearchImplicits
                             with SearchAssistants {}
}

object RichPostgresDriver extends RichPostgresDriver
