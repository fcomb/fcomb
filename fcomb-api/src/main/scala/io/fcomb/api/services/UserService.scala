package io.fcomb.api.services

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import io.fcomb.json._
import io.fcomb.persist
import io.fcomb.services.user.ResetPassword
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._

object UserService {
  val pathPrefix = "users"

  def signUp(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = complete("wow")
  // action { implicit ctx ⇒
  //   requestBodyAs[UserSignUpRequest] { req ⇒
  //     completeValidation(
  //       persist.User
  //         .create(
  //           email = req.email,
  //           username = req.username,
  //           fullName = req.fullName,
  //           password = req.password
  //         )
  //         .map(_.map(_.toResponse[UserProfileResponse])),
  //       StatusCodes.Created
  //     )
  //   }
  // }

  def me(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = complete("wow")
  //   authorizeUser { user ⇒
  //     complete(
  //       user.toResponse[UserProfileResponse],
  //       StatusCodes.OK
  //     )
  //   }
  // }

  def updateProfile(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = complete("wow")
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

  def changePassword(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = complete("wow")
  //   authorizeUser { user ⇒
  //     requestBodyAs[ChangePasswordRequest] { req ⇒
  //       completeWithoutContent(
  //         UsersRepo.changePassword(user, req.oldPassword, req.newPassword)
  //       )
  //     }
  //   }
  // }

  def resetPassword(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) = complete("wow")
  //   import sys.dispatcher
  //   requestBodyAs[ResetPasswordRequest] { req ⇒
  //     completeWithoutContent(
  //       ResetPassword.reset(req.email)
  //     )
  //   }
  // }

  def setPassword(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = complete("wow")
  // action { implicit ctx ⇒
  //     requestBodyAs[ResetPasswordSetRequest] { req ⇒
  //       completeWithoutContent(
  //         ResetPassword.set(req.token, req.password)
  //       )
  //     }
  //   }
}
