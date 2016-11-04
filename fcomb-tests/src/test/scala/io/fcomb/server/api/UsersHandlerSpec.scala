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

package io.fcomb.server.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.fcomb.json.rpc.Formats._
import io.fcomb.persist.UsersRepo
import io.fcomb.rpc.UserSignUpRequest
import io.fcomb.server.Api
import io.fcomb.server.CirceSupport._
import io.fcomb.tests._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

final class UsersHandlerSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with SpecHelpers
    with ScalaFutures
    with PersistSpec {
  val route = Api.routes

  "The users handler" should {
    "return a created when sign up with valid user data" in {
      val req = UserSignUpRequest(email = "user@fcomb.io",
                                  password = "drowssap",
                                  username = "user",
                                  fullName = Some("John Doe"))

      Post(s"/v1/users/sign_up", req) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        val user = UsersRepo.findByEmail("user@fcomb.io").futureValue.get
        user.username shouldEqual "user"
        user.fullName should contain("John Doe")
      }
    }
  }
}
