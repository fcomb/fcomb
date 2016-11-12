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
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.UserRole
import io.fcomb.persist.UsersRepo
import io.fcomb.rpc.helpers.UserHelpers
import io.fcomb.rpc._
import io.fcomb.server.Api
import io.fcomb.tests.AuthSpec._
import io.fcomb.tests.fixtures._
import io.fcomb.tests._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time._
import org.scalatest.{Matchers, WordSpec}

final class UsersHandlerSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with SpecHelpers
    with ScalaFutures
    with PersistSpec {
  val route = Api.routes

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1500, Millis))

  "The users handler" should {
    "return a list of users for user with admin role" in {
      val (admin, user1, user2) = (for {
        admin <- UsersFixture.create(role = UserRole.Admin, username = "admin")
        user1 <- UsersFixture.create(email = "user1@fcomb.io", username = "user1")
        user2 <- UsersFixture.create(email = "user2@fcomb.io", username = "user2")
      } yield (admin, user1, user2)).futureValue

      Get(s"/v1/users") ~> authenticate(admin) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[DataResponse[UserProfileResponse]].data should contain theSameElementsAs Seq(
          UserHelpers.profileResponse(admin),
          UserHelpers.profileResponse(user1),
          UserHelpers.profileResponse(user2))
      }
    }

    "return an error for list of users for user without admin role" in {
      val user = (for {
        user <- UsersFixture.create(email = "user1@fcomb.io", username = "user1")
        _    <- UsersFixture.create(email = "user2@fcomb.io", username = "user2")
      } yield user).futureValue

      Get(s"/v1/users") ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.Forbidden
      }
    }

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
