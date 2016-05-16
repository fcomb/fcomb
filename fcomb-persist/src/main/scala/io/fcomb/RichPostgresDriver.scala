package io.fcomb

import com.github.tminglei.slickpg._

trait RichPostgresDriver extends ExPostgresDriver
    with PgArraySupport
    with PgDateSupport
    with PgCirceJsonSupport
    with PgNetSupport
    with PgLTreeSupport
    with PgRangeSupport
    with PgHStoreSupport
    with PgEnumSupport
    with PgSearchSupport
    with PgDate2Support {

  override val pgjson = "json"

  override val api = new API with ArrayImplicits with DateTimeImplicits with Date2DateTimePlainImplicits with JsonImplicits with NetImplicits with LTreeImplicits with RangeImplicits with HStoreImplicits with SearchImplicits with SearchAssistants {}
}

object RichPostgresDriver extends RichPostgresDriver
