package io.fcomb.server

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers.OAuth2BearerToken
import io.fcomb.persist.SessionsRepo
import io.fcomb.models.User

trait AuthenticationDirectives {
  def authenticateUser: Directive1[User] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extractCredentials.flatMap {
        case Some(OAuth2BearerToken(token)) ⇒
          onSuccess(SessionsRepo.findById(token)).flatMap {
            case Some(user) ⇒ provide(user)
            case None       ⇒ reject(AuthorizationFailedRejection)
          }
        case _ ⇒ reject(AuthorizationFailedRejection)
      }
    }
}

object AuthenticationDirectives extends AuthenticationDirectives
