package io.fcomb.api.services

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import io.fcomb.json._
import io.fcomb.models.request._
import io.fcomb.models.response._
import io.fcomb.persist
import io.fcomb.services.user.ResetPassword
import scala.concurrent.ExecutionContext

object UserService extends Service {
  def signUp(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx =>
    requestBodyAs[UserSignUpRequest] { req =>
      completeValidation(
        persist.User.create(
          email = req.email,
          username = req.username,
          fullName = req.fullName,
          password = req.password
        ).map(_.map(_.toResponse[UserProfileResponse])),
        StatusCodes.Created
      )
    }
  }

  def me(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx =>
    authorizeUser { user =>
      complete(
        user.toResponse[UserProfileResponse],
        StatusCodes.OK
      )
    }
  }

  def updateProfile(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx =>
    authorizeUser { user =>
      requestBodyAs[UserRequest] { req =>
        completeValidation(
          persist.User.updateByRequest(user.getId)(
            email = req.email,
            username = req.username,
            fullName = req.fullName
          ).map(_.map(_.toResponse[UserProfileResponse])),
          StatusCodes.OK
        )
      }
    }
  }

  def changePassword(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx =>
    authorizeUser { user =>
      requestBodyAs[ChangePasswordRequest] { req =>
        completeWithoutContent(
          persist.User
            .changePassword(user, req.oldPassword, req.newPassword),
          StatusCodes.NoContent
        )
      }
    }
  }

  def resetPassword(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) = action { implicit ctx =>
    import sys.dispatcher
    requestBodyAs[ResetPasswordRequest] { req =>
      completeWithoutContent(
        ResetPassword.reset(req.email),
        StatusCodes.NoContent
      )
    }
  }

  def setPassword(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx =>
    requestBodyAs[ResetPasswordSetRequest] { req =>
      completeWithoutContent(
        ResetPassword.set(req.token, req.password),
        StatusCodes.NoContent
      )
    }
  }
}
