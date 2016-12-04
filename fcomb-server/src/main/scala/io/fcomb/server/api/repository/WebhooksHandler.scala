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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import cats.data.Validated
import io.fcomb.json.rpc.docker.distribution.Formats._
import io.fcomb.models.acl.Action
import io.fcomb.models.common.Slug
import io.fcomb.persist.docker.distribution.ImageWebhooksRepo
import io.fcomb.rpc.docker.distribution.ImageWebhookRequest
import io.fcomb.rpc.helpers.docker.distribution.ImageWebhookHelpers
import io.fcomb.server.api.{ApiHandler, ApiHandlerConfig}
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.PaginationDirectives._

final class WebhooksHandler(implicit val config: ApiHandlerConfig) extends ApiHandler {
  final def index(slug: Slug) =
    authenticateUser { user =>
      image(slug, user.getId(), Action.Read) { image =>
        extractPagination { pg =>
          onSuccess(ImageWebhooksRepo.paginateByImageId(image.getId(), pg)) { p =>
            completePagination(ImageWebhooksRepo.label, p)
          }
        }
      }
    }

  final def upsert(slug: Slug) =
    authenticateUser { user =>
      image(slug, user.getId(), Action.Write) { image =>
        entity(as[ImageWebhookRequest]) { req =>
          onSuccess(ImageWebhooksRepo.upsert(image.getId(), req.url)) {
            case Validated.Valid(webhook) =>
              val res = ImageWebhookHelpers.response(webhook)
              completeWithEtag(StatusCodes.OK, res)
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  final def routes(slug: Slug) =
    // format: OFF
    path("webhooks") {
      get(index(slug)) ~
      put(upsert(slug))
    }
    // format: ON
}
