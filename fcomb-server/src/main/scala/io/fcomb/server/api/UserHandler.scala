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

package io.fcomb.server.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.fcomb.server.CommonDirectives._
import io.fcomb.json.rpc.Formats.encodeUserProfileResponse
import io.fcomb.server.AuthenticationDirectives._
import io.fcomb.rpc.helpers.UserHelpers

object UserHandler {
  val handlerPath = "user"

  def current =
    authenticateUser { user =>
      completeWithEtag(StatusCodes.OK, UserHelpers.profileResponse(user))
    }

  // def updateProfile(
  //   implicit
  //   ec:  ExecutionContext,
  //   mat: Materializer
  // ) =
  // authorizeUser { user =>
  //   requestBodyAs[UserRequest] { req =>
  //     completeValidationWithoutContent(
  //       UsersRepo.update(user.getId())(
  //         email = req.email,
  //         username = req.username,
  //         fullName = req.fullName
  //       )
  //     )
  //   }
  // }

  // def changePassword(
  //   implicit
  //   ec:  ExecutionContext,
  //   mat: Materializer
  // ) =
  //   authorizeUser { user =>
  //     requestBodyAs[ChangePasswordRequest] { req =>
  //       completeWithoutContent(
  //         UsersRepo.changePassword(user, req.oldPassword, req.newPassword)
  //       )
  //     }
  //   }
  // }

  // def resetPassword(
  //   implicit
  //   sys: ActorSystem,
  //   mat: Materializer
  // ) =
  //   import sys.dispatcher
  //   requestBodyAs[ResetPasswordRequest] { req =>
  //     completeWithoutContent(
  //       ResetPassword.reset(req.email)
  //     )
  //   }
  // }

  // def setPassword(
  //   implicit
  //   ec:  ExecutionContext,
  //   mat: Materializer
  // ) =
  // action { implicit ctx =>
  //     requestBodyAs[ResetPasswordSetRequest] { req =>
  //       completeWithoutContent(
  //         ResetPassword.set(req.token, req.password)
  //       )
  //     }
  //   }

  val routes: Route = {
    // format: OFF
    pathPrefix(handlerPath) {
      pathEnd {
        get(current)
      } ~
      user.OrganizationsHandler.routes ~
      user.RepositoriesHandler.routes
    }
    // format: ON
  }
}
