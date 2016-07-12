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

package io.fcomb

import com.github.tminglei.slickpg._

trait FcombPostgresProfile
    extends ExPostgresDriver
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

  override val api = new API with ArrayImplicits with DateTimeImplicits
  with Date2DateTimePlainImplicits with JsonImplicits with NetImplicits with LTreeImplicits
  with RangeImplicits with HStoreImplicits with SearchImplicits with SearchAssistants {}
}

object FcombPostgresProfile extends FcombPostgresProfile
