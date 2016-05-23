package io.fcomb.api.services

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import io.fcomb.json._
import io.fcomb.request._
import io.fcomb.response._
import io.fcomb.persist
import scala.concurrent.{ExecutionContext, Future}

object SessionService extends Service {
  def create(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    requestBodyAs[SessionRequest] { req ⇒
      completeValidation(
        persist.Session.create(req),
        StatusCodes.OK
      )
    }
  }

  def destroy(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ) = action { implicit ctx ⇒
    authorizeUser { _ ⇒
      completeWithoutContent(
        getAuthToken match {
          case Some(token) ⇒ persist.Session.destroy(token)
          case None        ⇒ FastFuture.successful(())
        }
      )
    }
  }
}
