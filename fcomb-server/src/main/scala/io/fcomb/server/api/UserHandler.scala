package io.fcomb.server.api

import akka.http.scaladsl.model.StatusCodes
import io.fcomb.models.UserSignUpRequest
import io.fcomb.persist.UsersRepo
import akka.http.scaladsl.server.Directives._
import cats.data.Validated
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import akka.http.scaladsl.model.HttpResponse

object UserHandler {
  val pathPrefix = "users"

  def signUp =
    extractExecutionContext { implicit ec ⇒
      entity(as[UserSignUpRequest]) { req ⇒
        onSuccess(UsersRepo.create(req)) {
          case Validated.Valid(_)   ⇒ complete(HttpResponse(StatusCodes.Created))
          case Validated.Invalid(e) ⇒ complete(StatusCodes.BadRequest, e)
        }
      }
    }

  def me = complete("wow")
  //   authorizeUser { user ⇒
  //     complete(
  //       user.toResponse[UserProfileResponse],
  //       StatusCodes.OK
  //     )
  //   }
  // }

  // def updateProfile(
  //   implicit
  //   ec:  ExecutionContext,
  //   mat: Materializer
  // ) =
  // authorizeUser { user ⇒
  //   requestBodyAs[UserRequest] { req ⇒
  //     completeValidationWithoutContent(
  //       UsersRepo.updateByRequest(user.getId)(
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
  //   authorizeUser { user ⇒
  //     requestBodyAs[ChangePasswordRequest] { req ⇒
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
  //   requestBodyAs[ResetPasswordRequest] { req ⇒
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
  // action { implicit ctx ⇒
  //     requestBodyAs[ResetPasswordSetRequest] { req ⇒
  //       completeWithoutContent(
  //         ResetPassword.set(req.token, req.password)
  //       )
  //     }
  //   }
}
