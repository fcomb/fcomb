package io.fcomb.api.services.application

import io.fcomb.api.services.Service
import io.fcomb.json._
import io.fcomb.request._
import io.fcomb.response._
import io.fcomb.persist.application.{Application ⇒ PApplication}
import io.fcomb.models.errors.ExpectedAuthorizationToken
import io.fcomb.models.TokenRole
import io.fcomb.services.node.NodeManager
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import scala.concurrent.ExecutionContext
import scalaz._

object ApplicationService extends Service {
  val pathPrefix = "applications"

  def index(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    authorizeUser { user ⇒
      completeItems(
        PApplication.findAllByUserId(user.getId),
        StatusCodes.OK
      )
    }
  }

  def create(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    authorizeUser { user ⇒
      requestBodyAs[ApplicationRequest] { req ⇒
        completeValidationWithPkAsCreated(
          PApplication.createByRequest(user.getId, req),
          pathPrefix
        )
      }
    }
  }

  def show(id: Long)(
    implicit
    ec: ExecutionContext
  ) =
    action { implicit ctx ⇒
      authorizeUser { user ⇒
        completeOrNotFound(
          PApplication.findByIdAndUserId(id, user.getId),
          StatusCodes.OK
        )
      }
    }
}
