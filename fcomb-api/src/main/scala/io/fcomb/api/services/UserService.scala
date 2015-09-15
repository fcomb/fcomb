package io.fcomb.api.services

import akka.actor.ActorSystem
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import io.fcomb.json._
import io.fcomb.models._
import io.fcomb.models.ResponseConversions._
import io.fcomb.persist
import io.fcomb.services.user.ResetPassword
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.language.implicitConversions

object UserService extends Service {
  def test(implicit ec: ExecutionContext, mat: Materializer) =
    action { ctx =>
      ctx.requestBodyAs[UserSignUpRequest]().map { req =>
        println(s"req: $req")
      }
      ctx.complete("kek", StatusCodes.OK)
    }

  // def signUp(
  //   implicit
  //   ec:           ExecutionContext,
  //   materializer: Materializer
  // ) =
  //   requestAsWithValidation { req: UserSignUpRequest =>
  //     persist.User.create(
  //       email = req.email,
  //       username = req.username,
  //       fullName = req.fullName,
  //       password = req.password
  //     ).map(_.map(toResponse[User, UserResponse]))
  //   }

  // def me(
  //   implicit
  //   ec:           ExecutionContext,
  //   materializer: Materializer
  // ) =
  //   authorization { user =>
  //     responseAs[User, UserResponse](user)
  //   }

  // def updateProfile(
  //   implicit
  //   ec:           ExecutionContext,
  //   materializer: Materializer
  // ) =
  //   authorization { user =>
  //     requestAsWithValidation { req: UserRequest =>
  //       persist.User.updateByRequest(user.getId)(
  //         email = req.email,
  //         username = req.username,
  //         fullName = req.fullName
  //       ).map(_.map(toResponse[User, UserResponse]))
  //     }
  //   }

  // def changePassword(
  //   implicit
  //   ec:           ExecutionContext,
  //   materializer: Materializer
  // ) =
  //   authorization { user =>
  //     requestAsWithValidation[ChangePasswordRequest, NoContentResponse] { req =>
  //       persist.User
  //         .changePassword(user, req.oldPassword, req.newPassword)
  //         .map(_.map(_ => NoContentResponse()))
  //     }
  //   }

  // def resetPassword(
  //   implicit
  //   system:       ActorSystem,
  //   materializer: Materializer
  // ) = {
  //   import system.dispatcher

  //   requestAsWithValidation[ResetPasswordRequest, NoContentResponse] { req =>
  //     ResetPassword
  //       .reset(req.email)
  //       .map(_.map(_ => NoContentResponse()))
  //   }
  // }

  // def setPassword(
  //   implicit
  //   ec:           ExecutionContext,
  //   materializer: Materializer
  // ) =
  //   requestAsWithValidation[ResetPasswordSetRequest, NoContentResponse] { req =>
  //     ResetPassword
  //       .set(req.token, req.password)
  //       .map(_.map(_ => NoContentResponse()))
  //   }
}
