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

package io.fcomb.server.api.group

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.fcomb.models.common.Slug

object MembersHandler {
  val servicePath = "members"

  def index(slug: Slug) = ???

  def upsert(slug: Slug) = ???

  def destroy(slug: Slug, memberSlug: Slug) = ???

  def routes(slug: Slug): Route = {
    // format: OFF
    pathPrefix(servicePath) {
      pathEnd {
        get(index(slug)) ~
        put(upsert(slug))
      } ~
      path(Segment) { memberSlugSegment =>
        val memberSlug = Slug.parse(memberSlugSegment)
        delete(destroy(slug, memberSlug))
      }
    }
    // format: ON
  }
}
