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

package io.fcomb.server.api.organization.group

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.fcomb.json.models.errors.Formats.decodeErrors
import io.fcomb.json.rpc.Formats._
import io.fcomb.models.acl.Role
import io.fcomb.models.common.Slug
import io.fcomb.models.errors.Errors
import io.fcomb.persist.OrganizationGroupsRepo
import io.fcomb.rpc._
import io.fcomb.server.Api
import io.fcomb.server.CirceSupport._
import io.fcomb.tests.AuthSpec._
import io.fcomb.tests.fixtures._
import io.fcomb.tests._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time._
import org.scalatest.{Matchers, WordSpec}

final class MembersHandlerSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with SpecHelpers
    with ScalaFutures
    with PersistSpec {
  val route = Api.routes

  override implicit val patienceConfig = PatienceConfig(timeout = Span(1500, Millis))

  "The group members handler" should {
    "return an accepted when adding new user" in {
      val (user, newUser, org) = (for {
        user    <- UsersFixture.create()
        org     <- OrganizationsFixture.create(userId = user.getId())
        newUser <- UsersFixture.create(email = "some@user.io", username = "new_user")
      } yield (user, newUser, org)).futureValue
      val req = MemberUsernameRequest(newUser.username)

      Put(s"/v1/organizations/${org.name}/groups/admins/members", req) ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "return an accepted when adding self" in {
      val (user, org) = (for {
        user <- UsersFixture.create()
        org  <- OrganizationsFixture.create(userId = user.getId())
      } yield (user, org)).futureValue
      val req = MemberUsernameRequest(user.username)

      Put(s"/v1/organizations/${org.name}/groups/admins/members", req) ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "return an accepted when deleting new user" in {
      val (user, newUser, org) = (for {
        user        <- UsersFixture.create()
        org         <- OrganizationsFixture.create(userId = user.getId())
        Some(group) <- OrganizationGroupsRepo.findBySlug(org.getId(), Slug.Name("admins"))
        newUser     <- UsersFixture.create(email = "some@user.io", username = "new_user")
        _           <- OrganizationGroupUsersFixture.create(group.getId(), newUser.getId())
      } yield (user, newUser, org)).futureValue

      Delete(s"/v1/organizations/${org.name}/groups/admins/members/${newUser.username}") ~> authenticate(
        user) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }

    "return an error when deleting self" in {
      val (user, org) = (for {
        user <- UsersFixture.create()
        org  <- OrganizationsFixture.create(userId = user.getId())
      } yield (user, org)).futureValue
      val req = MemberUsernameRequest(user.username)

      Delete(s"/v1/organizations/${org.name}/groups/admins/members/${user.username}", req) ~> authenticate(
        user) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Errors].errors.head shouldEqual Errors
          .validation("Cannot remove yourself from the last own admin group", "user")
      }
    }

    "return an accepted when deleting self from another admin group" in {
      val (user, org, group) = (for {
        user  <- UsersFixture.create()
        org   <- OrganizationsFixture.create(userId = user.getId())
        group <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Admin)
        _     <- OrganizationGroupUsersFixture.create(group.getId(), user.getId())
      } yield (user, org, group)).futureValue

      Delete(s"/v1/organizations/${org.name}/groups/${group.name}/members/${user.username}") ~> authenticate(
        user) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }
  }
}
