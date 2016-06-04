package io.fcomb.api.services

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import io.fcomb.json._
import io.fcomb.persist
import scala.concurrent.{ExecutionContext, Future}
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._

object SessionService {
  def create(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = complete("wow")
  //   requestBodyAs[SessionRequest] { req ⇒
  //     completeValidation(
  //       SessionsRepo.create(req),
  //       StatusCodes.OK
  //     )
  //   }
  // }

  def destroy(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = complete("wow")
  //   authorizeUser { _ ⇒
  //     completeWithoutContent(
  //       getAuthToken match {
  //         case Some(token) ⇒ SessionsRepo.destroy(token)
  //         case None        ⇒ FastFuture.successful(())
  //       }
  //     )
  //   }
  // }
}
