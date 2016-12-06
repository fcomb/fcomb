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

import io.fcomb.models.docker.distribution.{Image, ImageVisibilityKind}
import io.fcomb.models.{Organization, Owner, OwnerKind, User}
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.tests.fixtures.Fixtures
import java.time.OffsetDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object ImagesFixture {
  private def create(owner: Owner,
                     userId: Int,
                     namespace: String,
                     imageName: String,
                     visibilityKind: ImageVisibilityKind): Future[Image] = {
    val image = Image(
      id = None,
      name = imageName,
      slug = s"$namespace/$imageName",
      owner = owner,
      visibilityKind = visibilityKind,
      description = "*test description*",
      createdByUserId = userId,
      createdAt = OffsetDateTime.now,
      updatedAt = None
    )
    ImagesRepo.create(image).map(Fixtures.get)
  }

  def create(user: User, imageName: String, visibilityKind: ImageVisibilityKind): Future[Image] =
    create(Owner(user.getId(), OwnerKind.User),
           user.getId(),
           user.username,
           imageName,
           visibilityKind)

  def create(org: Organization,
             imageName: String,
             visibilityKind: ImageVisibilityKind): Future[Image] =
    create(Owner(org.getId(), OwnerKind.Organization),
           org.ownerUserId,
           org.name,
           imageName,
           visibilityKind)
}
