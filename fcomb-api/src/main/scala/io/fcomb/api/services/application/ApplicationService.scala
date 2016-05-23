package io.fcomb.api.services.application

import io.fcomb.api.services._
import io.fcomb.json._
import io.fcomb.request._
import io.fcomb.response._
import io.fcomb.persist.application.{Application ⇒ PApplication}
import io.fcomb.models.errors.ExpectedAuthorizationToken
import io.fcomb.models.TokenRole
import io.fcomb.services.application.ApplicationManager
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import scala.concurrent.{Future, ExecutionContext}
import cats.data.Validated

object ApplicationService extends Service with ApplicationAuth {
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
          ApplicationManager.create(user.getId, req),
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

  def stop(id: Long)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    checkOwner(id) {
      completeWithoutContent(ApplicationManager.stop(id))
    }
  }

  def start(id: Long)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    checkOwner(id) {
      completeWithoutContent(ApplicationManager.start(id))
    }
  }

  def restart(id: Long)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    checkOwner(id) {
      completeWithoutContent(ApplicationManager.restart(id))
    }
  }

  def terminate(id: Long)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    checkOwner(id) {
      completeWithoutContent(ApplicationManager.terminate(id))
    }
  }

  def redeploy(id: Long)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    requestBodyAsOpt[ApplicationRedeployRequest] { req ⇒
      checkOwner(id) {
        completeWithoutContent {
          ApplicationManager.redeploy(id, req.map(_.scaleStrategy))
        }
      }
    }
  }

  def scale(id: Long)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    requestBodyAs[ApplicationScaleRequest] { req ⇒
      checkOwner(id) {
        completeWithoutContent {
          ApplicationManager.scale(id, req.numberOfContainers)
        }
      }
    }
  }
}
