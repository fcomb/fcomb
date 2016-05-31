package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._

trait AuthenticationDirectives {
  import io.fcomb.persist.User
  import io.fcomb.models.{User ⇒ MUser}

  def authenticationUserBasic: Directive1[MUser] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extractCredentials.flatMap {
        case Some(BasicHttpCredentials(username, password)) ⇒
          onSuccess(User.matchByUsernameAndPassword(username, password)).flatMap {
            case Some(user) ⇒ provide(user)
            case None       ⇒ reject(AuthorizationFailedRejection)
          }
        case _ ⇒ reject(AuthorizationFailedRejection)
      }
    }
}

object AuthenticationDirectives extends AuthenticationDirectives

import AuthenticationDirectives._

object AuthenticationService {
  def versionCheck =
    authenticationUserBasic { _ ⇒
      complete(versionCheckResponse)
    }

  private val versionCheckResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(`application/json`, "{}")
  )
}
