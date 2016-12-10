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

package io.fcomb.server.api.organization

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model._
import io.fcomb.json.models.errors.Formats.decodeErrors
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.models.errors.Errors
import io.fcomb.persist.OrganizationGroupsRepo
import io.fcomb.rpc.helpers.UserHelpers
import io.fcomb.rpc._
import io.fcomb.server.Api
import io.fcomb.tests.AuthSpec._
import io.fcomb.tests.fixtures._
import io.fcomb.tests._

final class GroupsHandlerSpec extends ApiHandlerSpec {
  val route = Api.routes()

  "The groups handler" should {
    "return an error when downgrading the last admin group" in {
      val (user, org) = (for {
        user <- UsersFixture.create()
        org  <- OrganizationsFixture.create(userId = user.getId())
        _    <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Creator)
      } yield (user, org)).futureValue
      val req = OrganizationGroupRequest("admins", Role.Creator)

      Put(s"/v1/organizations/${org.name}/groups/admins", req) ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Errors].errors.head shouldEqual Errors
          .validation("Cannot downgrade role of the last admin group", "role")
      }
    }

    "return an error when downgrading the last own admin group" in {
      val (user, org) = (for {
        user <- UsersFixture.create()
        org  <- OrganizationsFixture.create(userId = user.getId())
        _    <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Admin)
      } yield (user, org)).futureValue
      val req = OrganizationGroupRequest("admins", Role.Creator)

      Put(s"/v1/organizations/${org.name}/groups/admins", req) ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Errors].errors.head shouldEqual Errors
          .validation("Cannot downgrade role of the last admin group", "role")
      }
    }

    "return an accepted when downgrading one of the own admin group" in {
      val (user, org) = (for {
        user  <- UsersFixture.create()
        org   <- OrganizationsFixture.create(userId = user.getId())
        group <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Admin)
        _     <- OrganizationGroupUsersFixture.create(group.getId(), user.getId())
      } yield (user, org)).futureValue
      val req = OrganizationGroupRequest("admins", Role.Creator)

      Put(s"/v1/organizations/${org.name}/groups/admins", req) ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "return an error when deleting the last admin group" in {
      val (user, org) = (for {
        user <- UsersFixture.create()
        org  <- OrganizationsFixture.create(userId = user.getId())
        _    <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Creator)
      } yield (user, org)).futureValue

      Delete(s"/v1/organizations/${org.name}/groups/admins") ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Errors].errors.head shouldEqual Errors
          .validation("Cannot delete your last admin group", "id")
      }
    }

    "return an error when deleting the last own admin group" in {
      val (user, org) = (for {
        user <- UsersFixture.create()
        org  <- OrganizationsFixture.create(userId = user.getId())
        _    <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Admin)
      } yield (user, org)).futureValue

      Delete(s"/v1/organizations/${org.name}/groups/admins") ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Errors].errors.head shouldEqual Errors
          .validation("Cannot delete your last admin group", "id")
      }
    }

    "return an accepted when deleting one of the own admin group" in {
      val (user, org, group) = (for {
        user  <- UsersFixture.create()
        org   <- OrganizationsFixture.create(userId = user.getId())
        group <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Admin)
        _     <- OrganizationGroupUsersFixture.create(group.getId(), user.getId())
      } yield (user, org, group)).futureValue

      Delete(s"/v1/organizations/${org.name}/groups/${group.name}") ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "return a list of suggested users which not in group" in {
      val (user, org, user1, user2) = (for {
        user        <- UsersFixture.create()
        org         <- OrganizationsFixture.create(userId = user.getId())
        user1       <- UsersFixture.create(email = "user1@fcomb.io", username = "user1")
        user2       <- UsersFixture.create(email = "user2@fcomb.io", username = "user2")
        user3       <- UsersFixture.create(email = "user3@fcomb.io", username = "user3")
        Some(group) <- OrganizationGroupsRepo.findBySlug(org.getId(), Slug.Name("admins"))
        _           <- OrganizationGroupUsersFixture.create(group.getId(), user3.getId())
      } yield (user, org, user1, user2)).futureValue

      Get(s"/v1/organizations/${org.name}/groups/admins/suggestions/members?q=user") ~> authenticate(
        user) ~> route ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[DataResponse[UserProfileResponse]].data should contain theSameElementsAs Seq(
          UserHelpers.profileResponse(user1),
          UserHelpers.profileResponse(user2))
      }
    }
  }
}
