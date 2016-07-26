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

package io.fcomb.tests.fixtures.docker.distribution

import cats.data.Validated
import io.fcomb.models.{OwnerKind, Owner, User}
import io.fcomb.models.docker.distribution.{Image, ImageVisibilityKind}
import io.fcomb.persist.docker.distribution.ImagesRepo
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import java.time.ZonedDateTime

object ImagesRepoFixture {
  def create(user: User,
             imageName: String,
             visibilityKind: ImageVisibilityKind,
             description: String = ""): Future[Image] = {
    val owner = Owner(user.getId(), OwnerKind.User)
    val image = Image(
      id = None,
      name = imageName,
      slug = s"${user.username}/$imageName",
      owner = owner,
      visibilityKind = visibilityKind,
      description = description,
      createdByUserId = user.getId(),
      createdAt = ZonedDateTime.now,
      updatedAt = None
    )
    for {
      Validated.Valid(res) <- ImagesRepo.create(image)
    } yield res
  }
}
