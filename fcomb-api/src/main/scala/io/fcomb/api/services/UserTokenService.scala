package io.fcomb.api.services

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import io.fcomb.json._
import io.fcomb.request._
import io.fcomb.response._
import io.fcomb.persist
import io.fcomb.models
import scala.concurrent.ExecutionContext

object UserTokenService extends Service {
  val pathPrefix = "tokens"

  def index(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx =>
    authorizeUser { user =>
      completeItems(
        persist.UserToken.findAllByUserId(user.getId),
        StatusCodes.OK
      )
    }
  }
}
