package io.fcomb.api.services.agent

import io.fcomb.api.services.Service
import io.fcomb.json._
import io.fcomb.request._
import io.fcomb.response._
import io.fcomb.persist.node.{Node ⇒ PNode}
import io.fcomb.models.errors.ExpectedAuthorizationToken
import io.fcomb.models.TokenRole
import io.fcomb.services.node.NodeManager
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import scala.concurrent.ExecutionContext
import scalaz._

object NodeService extends Service {
  val pathPrefix = "nodes"

  def join(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    authorizeUserByToken(TokenRole.JoinCluster) { user ⇒
      requestBodyAs[NodeJoinRequest] { req ⇒
        complete(NodeManager.joinByRequest(user.getId, req).map {
          case Success(node) ⇒
            completeAsCreated(
              s"$apiPrefix/${AgentService.pathPrefix}/$pathPrefix/${node.getId}",
              List(Authorization(GenericHttpCredentials("Token", node.token)))
            )
          case Failure(e) ⇒ complete(e)
        })
      }
    }
  }

  def show(id: Long)(
    implicit
    ec: ExecutionContext
  ) =
    action { implicit ctx ⇒
      getAuthToken("token") match {
        case Some(token) ⇒
          completeOrNotFound(
            PNode.findByIdAndTokenAsAgentResponse(id, token),
            StatusCodes.OK
          )
        case None ⇒
          complete(mapAccessException(ExpectedAuthorizationToken))
      }
    }
}
