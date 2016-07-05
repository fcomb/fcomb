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
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.fcomb.json.models.errors.Formats._
import io.fcomb.json.rpc.acl.Formats._
import io.fcomb.models.acl.{Action, MemberKind}
import io.fcomb.models.docker.distribution.ImageKey
import io.fcomb.models.errors.{FailureResponse, UnknownEnumItemException}
import io.fcomb.persist.acl.PermissionsRepo
import io.fcomb.rpc.acl.PermissionUserCreateRequest
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.server.CommonDirectives._
import io.fcomb.server.ImageDirectives._
import io.fcomb.server.PaginationDirectives._

object PermissionsHandler {
  val servicePath = "permissions"

  def index(key: ImageKey) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Manage) { image =>
          extractPagination { pg =>
            onSuccess(PermissionsRepo.findByImageIdWithPagination(image, pg)) { p =>
              completePagination(PermissionsRepo.label, p)
            }
          }
        }
      }
    }
  }

  def upsert(key: ImageKey) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Manage) { image =>
          entity(as[PermissionUserCreateRequest]) { req =>
            onSuccess(PermissionsRepo.upsertByImage(image, req)) {
              case Validated.Valid(p)   => complete((StatusCodes.Accepted, p))
              case Validated.Invalid(e) => ??? // TODO
            }
          }
        }
      }
    }
  }

  def destroy(key: ImageKey, memberKind: MemberKind, slug: String) = {
    extractExecutionContext { implicit ec =>
      authenticateUser { user =>
        imageByKeyWithAcl(key, user, Action.Manage) { image =>
          onSuccess(PermissionsRepo.destroyByImage(image, memberKind, slug)) {
            case Validated.Valid(p)   => completeAccepted()
            case Validated.Invalid(e) => ??? // TODO
          }
        }
      }
    }
  }

  def routes(key: ImageKey): Route = {
    // format: OFF
    pathPrefix(servicePath) {
      pathEnd {
        get(index(key)) ~
        put(upsert(key))
      } ~
      path(Segment / Segment) { (kind, slug) =>
        extractMemberKind(kind) { memberKind =>
          delete(destroy(key, memberKind, slug))
        }
      }
    }
    // format: ON
  }

  private def extractMemberKind(kind: String): Directive1[MemberKind] = {
    MemberKind.withNameOption(kind) match {
      case Some(memberKind) => provide(memberKind)
      case None =>
        complete(
          (StatusCodes.BadRequest,
           FailureResponse.fromException(UnknownEnumItemException(kind, MemberKind))))
    }
  }
}
