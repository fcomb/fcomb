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

package io.fcomb.tests.fixtures

import cats.data.Validated
import io.fcomb.models.{User, UserRole}
import io.fcomb.persist.UsersRepo
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

object UsersRepoFixture {
  val email    = "test@fcomb.io"
  val username = "test"
  val password = "password"
  val fullName = Some("Test Test")

  def create(
      email: String = email,
      username: String = username,
      password: String = password,
      fullName: Option[String] = fullName,
      role: UserRole = UserRole.Developer
  ): Future[User] =
    for {
      Validated.Valid(user) <- UsersRepo.create(
        email = email,
        username = username,
        password = password,
        fullName = fullName,
        role = role
      )
    } yield user
}
