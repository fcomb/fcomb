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

package io.fcomb.docker.distribution.server.api

import io.fcomb.docker.distribution.server.headers.`Docker-Distribution-Api-Version`
import io.fcomb.tests.fixtures._
import io.fcomb.tests._
import org.scalatest.{Matchers, WordSpec}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.fcomb.docker.distribution.server.Routes
import io.circe.Json
import de.heikoseeberger.akkahttpcirce.CirceSupport._

class AuthenticationHandlerSpec extends WordSpec with Matchers with ScalatestRouteTest with PersistSpec {
  val route = Routes()
  val credentials = BasicHttpCredentials(UsersRepoFixture.username, UsersRepoFixture.password)

  "The authentication handler" should {
    "return a version for GET requests to the version path" in {
      Fixtures.await(UsersRepoFixture.create())

      Get("/v2/") ~> addCredentials(credentials) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        entityAs[Json] shouldEqual Json.obj()
        header[`Docker-Distribution-Api-Version`].get.value shouldEqual "registry/2.0"
      }
    }
  }
}
