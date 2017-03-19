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
import akka.util.ByteString
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.Role
import io.fcomb.models.docker.distribution.{ImageBlobState, ImageVisibilityKind}
import io.fcomb.persist.OrganizationsRepo
import io.fcomb.rpc._
import io.fcomb.server.Api
import io.fcomb.tests.AuthSpec._
import io.fcomb.tests.fixtures.docker.distribution._
import io.fcomb.tests.fixtures._
import io.fcomb.tests._

final class OrganizationsHandlerSpec extends ApiHandlerSpec {
  val route = Api.routes()

  "The organizations handler" should {
    "return a created when creating" in {
      val user = UsersFixture.create().futureValue
      val req  = OrganizationCreateRequest("new_test_group")

      Post("/v1/organizations", req) ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.Created
        val org = responseAs[OrganizationResponse]
        org.name shouldEqual req.name
        org.ownerUserId shouldEqual user.id
        org.role should contain(Role.Admin)
        OrganizationsRepo.findById(org.id).futureValue should not be empty
      }
    }

    "return an accepted when deleting" in {
      val (user, org) = (for {
        user  <- UsersFixture.create()
        org   <- OrganizationsFixture.create(userId = user.getId())
        _     <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Creator)
        image <- ImagesFixture.create(org, "test", ImageVisibilityKind.Private)
        blob1 <- ImageBlobsFixture.createAs(user.getId(),
                                            image.getId(),
                                            ByteString("test"),
                                            ImageBlobState.Uploaded)
        _ <- ImageManifestsFixture.createV2(user.getId(), image.slug, blob1, List("1.0"))
      } yield (user, org)).futureValue

      Delete(s"/v1/organizations/${org.name}") ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
        OrganizationsRepo.findById(org.getId()).futureValue shouldBe empty
      }
    }
  }
}
