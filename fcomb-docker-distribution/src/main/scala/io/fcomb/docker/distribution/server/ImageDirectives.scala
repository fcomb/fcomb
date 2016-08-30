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

package io.fcomb.docker.distribution.server

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server._
import cats.data.Xor
import cats.syntax.eq._
import io.fcomb.server.CirceSupport._
import io.fcomb.models.acl.Action
import io.fcomb.models.common.Slug
import io.fcomb.models.docker.distribution.{Image, ImageVisibilityKind}
import io.fcomb.models.errors.docker.distribution._
import io.fcomb.persist.docker.distribution.ImagesRepo
import io.fcomb.json.models.errors.docker.distribution.Formats._

object ImageDirectives {
  def imageByNameWithAcl(name: String, userId: Int, action: Action): Directive1[Image] = {
    extractExecutionContext.flatMap { implicit ec =>
      onSuccess(ImagesRepo.findBySlugWithAcl(Slug.Name(name), userId, action)).flatMap {
        case Xor.Right(Some((image, _))) => provide(image)
        case _                           => complete(nameUnknownError)
      }
    }
  }

  def imageByNameWithReadAcl(name: String, userIdOpt: Option[Int]): Directive1[Image] = {
    userIdOpt match {
      case Some(userId) => imageByNameWithAcl(name, userId, Action.Read)
      case _            => publicImageByName(name)
    }
  }

  private def publicImageByName(name: String): Directive1[Image] = {
    extractExecutionContext.flatMap { implicit ec =>
      onSuccess(ImagesRepo.findBySlug(Slug.Name(name))).flatMap {
        case Some(image) if image.visibilityKind === ImageVisibilityKind.Public =>
          provide(image)
        case _ => complete(nameUnknownError)
      }
    }
  }

  private val nameUnknownError = (
    StatusCodes.NotFound,
    DistributionErrorResponse.from(DistributionError.NameUnknown())
  )
}
