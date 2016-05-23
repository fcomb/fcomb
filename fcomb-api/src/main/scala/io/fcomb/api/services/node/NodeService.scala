package io.fcomb.api.services.node

import io.fcomb.api.services._
import io.fcomb.json._
import io.fcomb.request._
import io.fcomb.persist.node.{Node ⇒ PNode}
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.model.StatusCodes
import scala.concurrent.{ExecutionContext, Future}

object NodeService extends Service {
  val pathPrefix = "nodes"

  def index(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    authorizeUser { user ⇒
      completeItems(
        PNode.findAllByUserIdAsResponse(user.getId),
        StatusCodes.OK
      )
    }
  }

  def show(id: Long)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    authorizeUser { user ⇒
      completeOrNotFound(
        PNode.findByIdAndUserIdAsResponse(id, user.getId),
        StatusCodes.OK
      )
    }
  }
}
