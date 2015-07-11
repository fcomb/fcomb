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
import ResponseConversions._

object UserService extends ApiService {
  def signUp(
    implicit
    ec:           ExecutionContext,
    materializer: Materializer
  ) =
    requestAsWithValidation { user: UserSignUpRequest =>
      persist.User.create(
        email = user.email,
        username = user.username,
        fullName = user.fullName,
        password = user.password
      ).map(_.map(toResponse[User, UserResponse]))
    }
}
