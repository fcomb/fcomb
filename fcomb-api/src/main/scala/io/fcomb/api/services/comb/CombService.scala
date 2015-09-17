package io.fcomb.api.services.comb

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.stream.Materializer
import io.fcomb.api.services.Service
import io.fcomb.json._
import io.fcomb.request._
import io.fcomb.response._
import io.fcomb.persist
import scala.concurrent.ExecutionContext

object CombService extends Service {
  def create(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx =>
    authorizeUser { user =>
      requestBodyAs[CombRequest] { req =>
        completeValidation(
          persist.comb.Comb.create(
            userId = user.getId,
            name = req.name,
            slug = req.slug
          ).map(_.map(_.toResponse[CombResponse])),
          StatusCodes.Created
        )
      }
    }
  }

  def update(
    id: Long
  )(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx =>
    authorizeUser { user =>
      requestBodyAs[CombRequest] { req =>
        completeValidation(
          persist.comb.Comb.updateByRequest(id)(
            name = req.name,
            slug = req.slug
          ).map(_.map(_.toResponse[CombResponse])),
          StatusCodes.OK
        )
      }
    }
  }

  def show(
    id: Long
  )(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = ???

  def destroy(
    id: Long
  )(
    implicit
    ec: ExecutionContext,
    mat: Materializer
  ) = ???
}
