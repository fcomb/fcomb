package io.fcomb.docker.distribution.server

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import io.fcomb.persist.UsersRepo
import io.fcomb.models.User

trait AuthenticationDirectives {
  def authenticateUserBasic: Directive1[User] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extractCredentials.flatMap {
        case Some(BasicHttpCredentials(username, password)) ⇒
          onSuccess(UsersRepo.matchByUsernameAndPassword(username, password)).flatMap {
            case Some(user) ⇒ provide(user)
            case None       ⇒ reject(AuthorizationFailedRejection)
          }
        case _ ⇒ reject(AuthorizationFailedRejection)
      }
    }
}

object AuthenticationDirectives extends AuthenticationDirectives
