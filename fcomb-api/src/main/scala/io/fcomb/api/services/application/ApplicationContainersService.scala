package io.fcomb.api.services.application

import io.fcomb.api.services._
import io.fcomb.json._
import io.fcomb.request._
import io.fcomb.response._
import io.fcomb.persist.application.{Application ⇒ PApplication}
import io.fcomb.persist.docker.{Container ⇒ PContainer}
import io.fcomb.models.errors.ExpectedAuthorizationToken
import io.fcomb.models.TokenRole
import io.fcomb.services.application.ApplicationManager
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import scala.concurrent.{Future, ExecutionContext}
import scalaz._

object ApplicationContainersService extends Service with ApplicationAuth {
  val pathPrefix = "containers"

  def index(appId: Long)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    authorizeUser { user ⇒
      checkOwner(appId) {
        completeItems(
          PContainer.findAllByApplicationId(appId),
          StatusCodes.OK
        )
      }
    }
  }

  def show(appId: Long, id: Long)(
    implicit
    ec: ExecutionContext
  ) =
    action { implicit ctx ⇒
      authorizeUser { user ⇒
        checkOwner(appId) {
          completeOrNotFound(
            PContainer.findByApplicationIdAndId(appId, id),
            StatusCodes.OK
          )
        }
      }
    }

  // def stop(id: Long)(
  //   implicit
  //   ec:  ExecutionContext,
  //   mat: Materializer
  // ) = action { implicit ctx ⇒
  //   checkOwner(id) {
  //     completeWithoutContent(ApplicationManager.stop(id))
  //   }
  // }

  // def start(id: Long)(
  //   implicit
  //   ec:  ExecutionContext,
  //   mat: Materializer
  // ) = action { implicit ctx ⇒
  //   checkOwner(id) {
  //     completeWithoutContent(ApplicationManager.start(id))
  //   }
  // }

  // def terminate(id: Long)(
  //   implicit
  //   ec:  ExecutionContext,
  //   mat: Materializer
  // ) = action { implicit ctx ⇒
  //   checkOwner(id) {
  //     completeWithoutContent(ApplicationManager.terminate(id))
  //   }
  // }

  // def redeploy(id: Long)(
  //   implicit
  //   ec:  ExecutionContext,
  //   mat: Materializer
  // ) = action { implicit ctx ⇒
  //   checkOwner(id) {
  //     completeWithoutContent(ApplicationManager.redeploy(id))
  //   }
  // }

  // def scale(id: Long)(
  //   implicit
  //   ec:  ExecutionContext,
  //   mat: Materializer
  // ) = action { implicit ctx ⇒
  //   checkOwner(id) {
  //     // TODO
  //     completeWithoutContent(ApplicationManager.scale(id, 5))
  //   }
  // }

  // private def checkOwner(id: Long)(f: ⇒ ServiceResult)(
  //   implicit
  //   ctx: ServiceContext,
  //   ec:  ExecutionContext
  // ) =
  //   authorizeUser { user ⇒
  //     complete(PApplication.isOwner(user.getId, id).map {
  //       case true  ⇒ f
  //       case false ⇒ completeNotFound()
  //     })
  //   }
}
