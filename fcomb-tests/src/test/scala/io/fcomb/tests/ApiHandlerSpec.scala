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

import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import io.fcomb.akka.http.CirceSupport
import io.fcomb.Db
import io.fcomb.server.ApiHandlerConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time._
import org.scalatest._

abstract class ApiHandlerSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with SpecHelpers
    with ScalaFutures
    with PersistSpec
    with CirceSupport {
  final implicit val mat = ActorMaterializer()
  final implicit val ec = system.dispatcher
  final implicit val config = ApiHandlerConfig()(system, mat, Db.db)

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1500, Millis))

  val route: Route
}
