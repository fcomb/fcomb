package io.fcomb.api.services.node

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import io.fcomb.api.services.Service
import io.fcomb.json._
import io.fcomb.request._
import io.fcomb.response._
import io.fcomb.persist.node.{Node ⇒ PNode}
import io.fcomb.models.TokenRole
import io.fcomb.services.NodeManager
import scala.concurrent.ExecutionContext

object NodeService extends Service {
  val pathPrefix = "nodes"

  def join(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    authorizeUserByToken(TokenRole.JoinCluster) { user ⇒
      requestBodyAs[NodeJoinRequest] { req ⇒
        // TODO: add authorization header with node token!
        completeValidationWithPkAsCreated(
          NodeManager.joinByRequest(user.getId, req),
          ""
        )
      }
    }
  }

  // def update(
  //   id: Long
  // )(
  //   implicit
  //   ec: ExecutionContext,
  //   mat: Materializer
  // ) = action { implicit ctx =>
  //   authorizeUser { user =>
  //     requestBodyAs[CombRequest] { req =>
  //       completeValidation(
  //         persist.comb.Comb.updateByRequest(id)(
  //           name = req.name,
  //           slug = req.slug
  //         ).map(_.map(_.toResponse[CombResponse])),
  //         StatusCodes.OK
  //       )
  //     }
  //   }
  // }

  // def show(
  //   id: Long
  // )(
  //   implicit
  //   ec: ExecutionContext,
  //   mat: Materializer
  // ) = ???

  // def destroy(
  //   id: Long
  // )(
  //   implicit
  //   ec: ExecutionContext,
  //   mat: Materializer
  // ) = ???
}
