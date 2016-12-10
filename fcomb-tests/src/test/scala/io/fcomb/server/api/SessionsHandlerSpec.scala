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
import io.fcomb.crypto.Jwt
import io.fcomb.json.models.errors.Formats.decodeErrors
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.errors.Errors
import io.fcomb.models.{SessionPayload, UserRole}
import io.fcomb.rpc._
import io.fcomb.server.Api
import io.fcomb.tests.fixtures._
import io.fcomb.tests._
import java.time.Instant

final class SessionsHandlerSpec extends ApiHandlerSpec {
  val route = Api.routes()

  override def beforeAll(): Unit = {
    super.beforeAll()

    // FIXME: warming jwt-scala
    Jwt.encode(SessionPayload.User(0, "noname", UserRole.User), "secret", Instant.now(), 1)
    ()
  }

  "The sessions handler" should {
    "return a created when sign in with valid user data" in {
      UsersFixture.create().futureValue
      val req = SessionCreateRequest(UsersFixture.email, UsersFixture.password)

      Post("/v1/sessions", req) ~> route ~> check {
        status shouldEqual StatusCodes.Created
      }
    }

    "return an error when sign in with invalid user data" in {
      UsersFixture.create().futureValue
      val req = SessionCreateRequest(UsersFixture.email, s"${UsersFixture.password}_")

      Post("/v1/sessions", req) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Errors].errors should contain theSameElementsAs Seq(
          Errors.validation("invalid", "email"),
          Errors.validation("invalid", "password"))
      }
    }
  }
}
