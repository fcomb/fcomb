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
import cats.data.Xor
import io.fcomb.models.User
import io.fcomb.models.acl.Action
import io.fcomb.models.common.Slug
import io.fcomb.models.docker.distribution.{Image, ImageVisibilityKind}
import io.fcomb.persist.docker.distribution.ImagesRepo

object ImageDirectives {
  def imageWithActionBySlugWithAcl(slug: Slug,
                                   userId: Int,
                                   action: Action): Directive1[(Image, Action)] = {
    extractExecutionContext.flatMap { implicit ec =>
      onSuccess(ImagesRepo.findBySlugWithAcl(slug, userId, action)).flatMap {
        case Xor.Right(Some(res @ (image, _))) => provide(res)
        case Xor.Right(_)                      => complete(HttpResponse(StatusCodes.NotFound))
        case _                                 => complete(HttpResponse(StatusCodes.Forbidden))
      }
    }
  }

  def imageBySlugWithAcl(slug: Slug, userId: Int, action: Action): Directive1[Image] = {
    imageWithActionBySlugWithAcl(slug, userId, action).flatMap {
      case (image, _) => provide(image)
    }
  }

  def imageBySlugRead(slug: Slug, userOpt: Option[User]): Directive1[Image] = {
    userOpt match {
      case Some(user) => imageBySlugWithAcl(slug, user.getId(), Action.Read)
      case _ =>
        extractExecutionContext.flatMap { implicit ec =>
          onSuccess(ImagesRepo.findBySlug(slug)).flatMap {
            case Some(image) if image.visibilityKind == ImageVisibilityKind.Public =>
              provide(image)
            case Some(image) => complete(HttpResponse(StatusCodes.Forbidden))
            case _           => complete(HttpResponse(StatusCodes.NotFound))
          }
        }
    }
  }
}
