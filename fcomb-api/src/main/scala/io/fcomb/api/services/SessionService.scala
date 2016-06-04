package io.fcomb.api.services

import akka.http.scaladsl.model.StatusCodes
import io.fcomb.persist.SessionsRepo
import io.fcomb.models.SessionCreateRequest
import akka.http.scaladsl.server.Directives._
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import cats.data.Xor
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.headers.OAuth2BearerToken

object SessionService {
  def create =
    extractExecutionContext { implicit ec ⇒
      entity(as[SessionCreateRequest]) { req ⇒
        onSuccess(SessionsRepo.create(req)) {
          case Xor.Right(s) ⇒ complete(StatusCodes.OK, s)
          case Xor.Left(e)  ⇒ complete(StatusCodes.BadRequest, e)
        }
      }
    }

  def destroy =
    extractExecutionContext { implicit ec ⇒
      extractCredentials {
        case Some(OAuth2BearerToken(token)) ⇒
          onSuccess(SessionsRepo.destroy(token)) { _ ⇒
            complete(HttpResponse(StatusCodes.NoContent))
          }
        case Some(_) ⇒ complete(HttpResponse(StatusCodes.BadRequest))
        case None    ⇒ complete(HttpResponse(StatusCodes.Unauthorized))
      }
    }
}
