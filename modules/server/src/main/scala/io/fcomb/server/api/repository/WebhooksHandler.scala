/*
 * Copyright 2017 fcomb. <https://fcomb.io>
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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import cats.data.Validated
import io.fcomb.akka.http.CirceSupport._
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.common.Slug
import io.fcomb.persist.docker.distribution.ImageWebhooksRepo
import io.fcomb.rpc.docker.distribution.ImageWebhookRequest
import io.fcomb.rpc.helpers.docker.distribution.ImageWebhookHelpers
import io.fcomb.server.ApiHandlerConfig
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.PaginationDirectives._
import io.fcomb.server.PersistDirectives._

object WebhooksHandler {
  def index(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      image(slug, user.getId(), Action.Read).apply { image =>
        extractPagination { pg =>
          import config.ec
          transact(ImageWebhooksRepo.paginateByImageId(image.getId(), pg))
            .apply(completePagination(ImageWebhooksRepo.label, _))
        }
      }
    }

  def upsert(slug: Slug)(implicit config: ApiHandlerConfig) =
    authenticateUser.apply { user =>
      image(slug, user.getId(), Action.Write).apply { image =>
        entity(as[ImageWebhookRequest]) { req =>
          import config.ec
          transact(ImageWebhooksRepo.upsert(image.getId(), req.url)).apply {
            case Validated.Valid(webhook) =>
              val res = ImageWebhookHelpers.response(webhook)
              completeWithEtag(StatusCodes.OK, res)
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  def routes(slug: Slug)(implicit config: ApiHandlerConfig) =
    // format: off
    path("webhooks") {
      get(index(slug)) ~
      put(upsert(slug))
    }
    // format: on
}
