package io.fcomb.api.services

import io.fcomb.models._
import io.fcomb.persist
import io.fcomb.json._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import scala.language.implicitConversions
import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.mutable
import shapeless._, contrib.scalaz._, syntax.std.function._
import scalaz._
import scalaz.syntax.foldable._

object UserService extends ApiService {
  def create(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer
  ) =
    requestAs { user: UserRequest =>
      // val res = sequence(fields).map((persist.User.create _).toProduct)
      // jsonResponse[models.User](res.toOption.get)
      UserResponse(java.util.UUID.randomUUID, user.email, user.username, user.fullName)
    }

  // import akka.http.scaladsl.model.headers.Authorization
  // def me(ctx: RequestContext)(implicit ec: ExecutionContext, materializer: ActorMaterializer) = {
  //   ctx.request.headers.collectFirst {
  //     case a: Authorization ⇒ a
  //   } match {
  //     case Some(token) =>
  //       token.value.split(" ") match {
  //         case Array("Token" | "token", token) =>
  //           val res = persist.Session.findById(token).flatMap {
  //             case Some(user) =>
  //               Future.successful(jsonResponse(user))
  //             case None =>
  //               Future.successful(unauthorizedError("Invalid token"))
  //           }
  //           ctx.complete(res)
  //         case _ =>
  //           ctx.complete(unauthorizedError("Expected format 'Token <token>'"))
  //       }
  //     case None =>
  //       ctx.complete(unauthorizedError("Expected 'Authorization' header with token"))
  //   }
  // }
}
