/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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
import io.fcomb.json.models.errors.Formats.decodeErrors
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.errors.Errors
import io.fcomb.models.UserRole
import io.fcomb.persist.UsersRepo
import io.fcomb.rpc.helpers.UserHelpers
import io.fcomb.rpc._
import io.fcomb.server.Api
import io.fcomb.tests.AuthSpec._
import io.fcomb.tests.fixtures._
import io.fcomb.tests._

final class UsersHandlerSpec extends ApiHandlerSpec {
  val route = Api.routes()

  "The users handler" should {
    "return a list of users for user with admin role" in {
      val (admin, user1, user2) = (for {
        admin <- UsersFixture.create(role = UserRole.Admin, username = "admin")
        user1 <- UsersFixture.create(email = "user1@fcomb.io", username = "user1")
        user2 <- UsersFixture.create(email = "user2@fcomb.io", username = "user2")
      } yield (admin, user1, user2)).futureValue

      Get(s"/v1/users") ~> authenticate(admin) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[DataResponse[UserResponse]].data should contain theSameElementsAs Seq(
          UserHelpers.response(admin),
          UserHelpers.response(user1),
          UserHelpers.response(user2))
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
        val user = UsersRepo.findByEmail(req.email).futureValue.get
        user.username shouldEqual req.username
        user.email shouldEqual req.email
        user.fullName shouldEqual req.fullName
      }
    }

    "return an user reponse for user with admin role" in {
      val (admin, user) = (for {
        admin <- UsersFixture.create(role = UserRole.Admin, username = "admin")
        user  <- UsersFixture.create(email = "user1@fcomb.io", username = "user1")
      } yield (admin, user)).futureValue

      Get(s"/v1/users/${user.username}") ~> authenticate(admin) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[UserResponse] shouldEqual UserHelpers.response(user)
      }
    }

    "return a created when submit by user with admin role with valid user data" in {
      val admin = UsersFixture.create(role = UserRole.Admin, username = "admin").futureValue
      val req = UserCreateRequest(email = "user@fcomb.io",
                                  password = "drowssap",
                                  username = "user",
                                  fullName = Some("John Doe"),
                                  role = UserRole.Admin)

      Post(s"/v1/users", req) ~> authenticate(admin) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        val res = responseAs[UserResponse]
        res.username shouldEqual req.username
        res.email shouldEqual req.email
        res.fullName shouldEqual req.fullName
        res.role shouldEqual req.role
        val user = UsersRepo.findByEmail(req.email).futureValue.get
        res shouldEqual UserHelpers.response(user)
      }
    }

    "return an accepted when update by user with admin role" in {
      val (admin, user) = (for {
        admin <- UsersFixture.create(role = UserRole.Admin, username = "admin")
        user  <- UsersFixture.create(email = "user1@fcomb.io", username = "user1")
      } yield (admin, user)).futureValue
      val req = UserUpdateRequest(email = "newuser@fcomb.io",
                                  password = Some("newpass"),
                                  fullName = Some("New Name"),
                                  role = UserRole.Admin)

      Put(s"/v1/users/${user.username}", req) ~> authenticate(admin) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
        val res = responseAs[UserResponse]
        res.email shouldEqual req.email
        res.fullName shouldEqual req.fullName
        res.role shouldEqual req.role
        val user = UsersRepo.findByEmail(req.email).futureValue.get
        res shouldEqual UserHelpers.response(user)
        user.isValidPassword("newpass") should be(true)
      }
    }

    "return an error when downgrade yourself" in {
      val admin = UsersFixture.create(role = UserRole.Admin, username = "admin").futureValue
      val req = UserUpdateRequest(email = "newuser@fcomb.io",
                                  password = Some("newpass"),
                                  fullName = Some("New Name"),
                                  role = UserRole.User)

      Put(s"/v1/users/${admin.username}", req) ~> authenticate(admin) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Errors].errors.head shouldEqual Errors.validation("Cannot downgrade yourself",
                                                                     "role")
      }
    }

    "return an accepted when delete by user with admin role" in {
      val (admin, user) = (for {
        admin <- UsersFixture.create(role = UserRole.Admin, username = "admin")
        user  <- UsersFixture.create(email = "user1@fcomb.io", username = "user1")
      } yield (admin, user)).futureValue

      Delete(s"/v1/users/${user.username}") ~> authenticate(admin) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
        UsersRepo.findById(user.getId()).futureValue shouldBe empty
      }
    }

    "return an error when delete yourself" in {
      val admin = UsersFixture.create(role = UserRole.Admin, username = "admin").futureValue

      Delete(s"/v1/users/${admin.username}") ~> authenticate(admin) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Errors].errors.head shouldEqual Errors.validation("Cannot delete yourself",
                                                                     "id")
      }
    }
  }
}
