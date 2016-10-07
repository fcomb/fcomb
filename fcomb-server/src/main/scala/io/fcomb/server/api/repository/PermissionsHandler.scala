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
import akka.http.scaladsl.server.{Directive1, Route}
import cats.data.Validated
import io.fcomb.json.models.errors.Formats._
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.json.rpc.Formats.encodeDataResponse
import io.fcomb.models.acl.{Action, MemberKind}
import io.fcomb.models.common.Slug
import io.fcomb.models.errors.Errors
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.rpc.acl.PermissionCreateRequest
import io.fcomb.rpc.DataResponse
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CirceSupport._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ErrorDirectives._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.PaginationDirectives._

object PermissionsHandler {
  val handlerPath = "permissions"

  def index(slug: Slug) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        image(slug, user.getId(), Action.Manage) { image =>
          extractPagination { pg =>
            onSuccess(PermissionsRepo.paginateByImageId(image, pg)) { p =>
              completePagination(PermissionsRepo.label, p)
            }
          }
        }
      }
    }

  def upsert(slug: Slug) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        image(slug, user.getId(), Action.Manage) { image =>
          entity(as[PermissionCreateRequest]) { req =>
            onSuccess(PermissionsRepo.upsertByImage(image, req)) {
              case Validated.Valid(p)      => complete((StatusCodes.Accepted, p))
              case Validated.Invalid(errs) => completeErrors(errs)
            }
          }
        }
      }
    }

  def destroy(slug: Slug, memberKind: MemberKind, memberSlug: Slug) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        image(slug, user.getId(), Action.Manage) { image =>
          onSuccess(PermissionsRepo.destroyByImage(image, memberKind, memberSlug)) {
            case Validated.Valid(p)      => completeAccepted()
            case Validated.Invalid(errs) => completeErrors(errs)
          }
        }
      }
    }

  def suggestions(slug: Slug) =
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        image(slug, user.getId(), Action.Manage) { image =>
          parameter('q) { q =>
            onSuccess(PermissionsRepo.findSuggestions(image, q)) { p =>
              completeWithEtag(StatusCodes.OK, DataResponse(p))
            }
          }
        }
      }
    }

  def routes(slug: Slug): Route =
    // format: OFF
    pathPrefix(handlerPath) {
      pathEnd {
        get(index(slug)) ~
        put(upsert(slug))
      } ~
      path("members_suggestions")(get(suggestions(slug))) ~
      path(Segment / Segment) { (kind, memberSlugSegment) =>
        extractMemberKind(kind) { memberKind =>
          val memberSlug = Slug.parse(memberSlugSegment)
          delete(destroy(slug, memberKind, memberSlug))
        }
      }
    }
    // format: ON

  private def extractMemberKind(kind: String): Directive1[MemberKind] =
    MemberKind.withNameOption(kind) match {
      case Some(memberKind) => provide(memberKind)
      case None =>
        complete((StatusCodes.BadRequest, Errors.from(Errors.unknownEnumItem(kind, MemberKind))))
    }
}
