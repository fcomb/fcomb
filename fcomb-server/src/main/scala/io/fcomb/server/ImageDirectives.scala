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

package io.fcomb.server

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import io.fcomb.server.CirceSupport._
import io.circe.generic.auto._
import io.fcomb.models.User
import io.fcomb.models.acl.Action
import io.fcomb.models.docker.distribution.{Image, ImageKey}
import io.fcomb.models.errors.docker.distribution._
import io.fcomb.persist.docker.distribution.ImagesRepo

trait ImageDirectives {
  final def imageByNameWithAcl(slug: String, user: User, action: Action): Directive1[Image] = {
    extractExecutionContext.flatMap { implicit ec =>
      onSuccess(ImagesRepo.findBySlugWithAcl(slug, user.getId(), action)).flatMap(provideImage)
    }
  }

  final def imageByIdWithAcl(id: Int, user: User, action: Action): Directive1[Image] = {
    extractExecutionContext.flatMap { implicit ec =>
      onSuccess(ImagesRepo.findByIdWithAcl(id, user.getId(), action)).flatMap(provideImage)
    }
  }

  final def imageByKeyWithAcl(key: ImageKey, user: User, action: Action): Directive1[Image] = {
    key match {
      case ImageKey.Id(id)     => imageByIdWithAcl(id, user, action)
      case ImageKey.Name(name) => imageByNameWithAcl(name, user, action)
    }
  }

  private final def provideImage(imageOpt: Option[Image]): Directive1[Image] = {
    imageOpt match {
      case Some(image) => provide(image)
      case None =>
        complete(
          (
            StatusCodes.NotFound,
            DistributionErrorResponse.from(DistributionError.NameUnknown())
          ))
    }
  }
}

object ImageDirectives extends ImageDirectives
