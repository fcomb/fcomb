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
    requestAsWithValidation { user: UserRequest =>
      persist.User.create(
        email = user.email,
        username = user.username,
        fullName = user.fullName,
        password = user.password
      ).map(_.map { res =>
        UserResponse(res.id, res.email, res.username, res.fullName)
      })
    }
}
