package io.fcomb.api.services

import io.fcomb.models
import io.fcomb.persist
import io.fcomb.utils.validation._
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorFlowMaterializer
import scala.language.implicitConversions
import scala.concurrent.{ ExecutionContext, Future }
import scala.collection.mutable
import spray.json._
import shapeless._, contrib.scalaz._, syntax.std.function._
import scalaz._
import scalaz.syntax.foldable._

// trait UserServiceProtocols extends place.PlaceCountryProtocols { this: DefaultJsonProtocol =>
//   case class UserResponse(
//     id:          String,
//     email:       String,
//     firstName:   Option[String],
//     lastName:    Option[String],
//     phoneNumber: Option[String],
//     rights:      List[RoleType]
//   )
//
//   implicit val userJsonResponseFormat = jsonFormat6(UserResponse)
// }

object UserService extends JsonService {
  def create(implicit ec: ExecutionContext, materializer: ActorFlowMaterializer) = jsonRequest { json =>
    val fields = json.get[String]("username", Some(_.present.max(255))) ::
      json.getOpt[String]("email", Some(_.email.max(255))) ::
      json.get[String]("password", Some(_.present.range(6, 50))) ::
      json.getOpt[String]("fullName") ::
      HNil
    // val res = sequence(fields).map((persist.User.create _).toProduct).map(_.map(_.map(user2Json)))
    // jsonResponse[UserResponse](res)
    ???
  }

  // private def user2Json(u: models.User) =
  //   UserResponse(
  //     id = u.id.toString(),
  //     email = u.email,
  //     firstName = u.firstName,
  //     lastName = u.lastName,
  //     phoneNumber = u.phoneNumber,
  //     rights = u.rights
  //   )

  // import akka.http.scaladsl.model.headers.Authorization
  // def me(ctx: RequestContext)(implicit ec: ExecutionContext, materializer: ActorFlowMaterializer) = {
  //   ctx.request.headers.collectFirst {
  //     case a: Authorization ⇒ a
  //   } match {
  //     case Some(token) =>
  //       token.value.split(" ") match {
  //         case Array("Token" | "token", token) =>
  //           val res = persist.Session.findById(token).flatMap {
  //             case Some(user) =>
  //               Future.successful(jsonResponse(user2Json(user)))
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
