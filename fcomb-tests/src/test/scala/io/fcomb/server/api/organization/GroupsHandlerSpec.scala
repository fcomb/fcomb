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
import akka.http.scaladsl.testkit.ScalatestRouteTest
import io.fcomb.json.models.errors.Formats.decodeErrors
import io.fcomb.models.acl.Role
import io.fcomb.models.errors.Errors
import io.fcomb.server.Api
import io.fcomb.server.CirceSupport._
import io.fcomb.tests.AuthSpec._
import io.fcomb.tests.fixtures._
import io.fcomb.tests._
import org.scalatest.{Matchers, WordSpec}

final class GroupsHandlerSpec
    extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with SpecHelpers
    with PersistSpec {
  val route = Api.routes

  "The groups handler" should {
    "return an error when deleting an admins group" in {
      val (org, user) = Fixtures.await(for {
        user <- UsersFixture.create()
        org  <- OrganizationsFixture.create(userId = user.getId())
        _    <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Creator)
      } yield (org, user))

      Delete(s"/v1/organizations/${org.name}/groups/admins") ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.BadRequest
        responseAs[Errors].errors.head shouldEqual Errors
          .validation("Cannot delete the last admin group", "id")
      }
    }

    "return an accepted when deleting one of admins group" in {
      val (org, user) = Fixtures.await(for {
        user <- UsersFixture.create()
        org  <- OrganizationsFixture.create(userId = user.getId())
        _    <- OrganizationGroupsFixture.create(orgId = org.getId(), role = Role.Admin)
      } yield (org, user))

      Delete(s"/v1/organizations/${org.name}/groups/admins") ~> authenticate(user) ~> route ~> check {
        status shouldEqual StatusCodes.Accepted
      }
    }
  }
}
