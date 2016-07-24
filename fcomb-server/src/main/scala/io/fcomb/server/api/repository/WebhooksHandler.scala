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

package io.fcomb.server.api.repository

import akka.http.scaladsl.server.Directives._
import cats.data.Validated
import io.circe.generic.auto._
import io.fcomb.models.acl.Action
import io.fcomb.models.common.Slug
import io.fcomb.models.docker.distribution.{ImageWebhooksPutRequest, ImageWebhooksResponse}
import io.fcomb.persist.docker.distribution.ImageWebhooksRepo
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CirceSupport._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.PaginationDirectives._

object WebhooksHandler {
  val servicePath = "webhooks"

  def index(slug: Slug) = {
    authenticateUserBasic { user =>
      extractMaterializer { implicit mat =>
        import mat.executionContext
        imageBySlugWithAcl(slug, user.getId(), Action.Read) { image =>
          extractPagination { pg =>
            onSuccess(ImageWebhooksRepo.findByImageId(image.getId(), pg)) { p =>
              completePagination(ImageWebhooksRepo.label, p)
            }
          }
        }
      }
    }
  }

  def upsert(slug: Slug) = {
    authenticateUserBasic { user =>
      extractMaterializer { implicit mat =>
        import mat.executionContext
        imageBySlugWithAcl(slug, user.getId(), Action.Write) { image =>
          entity(as[ImageWebhooksPutRequest]) { putRequest =>
            onSuccess(ImageWebhooksRepo.upsert(image.getId(), putRequest.url)) {
              case Validated.Valid(upserted) =>
                complete(ImageWebhooksResponse(image.name, Seq(upserted.url)))
              case _ => complete(ImageWebhooksResponse(image.name, Seq()))
            }
          }
        }
      }
    }
  }

  def routes(slug: Slug) = {
    // format: OFF
    path(servicePath) {
      get(index(slug)) ~
      put(upsert(slug))
    }
    // format: ON
  }
}
