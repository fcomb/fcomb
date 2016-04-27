package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives._
import akka.http.scaladsl.server.Directives._
import io.fcomb.utils.Config, Config.docker.distribution.realm

// TODO: move
trait AuthDirectives {
  import BasicDirectives._
  import FutureDirectives._
  import RouteDirectives._

  import akka.http.scaladsl.server.AuthenticationFailedRejection.{CredentialsRejected, CredentialsMissing}

  import io.fcomb.persist.User
  import io.fcomb.models.{User ⇒ MUser}

  def authenticateUserBasic(realm: String): Directive1[MUser] =
    extractExecutionContext.flatMap { implicit ec ⇒
      extractCredentials.flatMap {
        case Some(BasicHttpCredentials(username, password)) ⇒
          onSuccess(User.matchByUsernameAndPassword(username, password)).flatMap {
            case Some(user) ⇒ provide(user)
            case None ⇒
              reject(AuthenticationFailedRejection(CredentialsRejected, challengeFor(realm)))
          }
        case _ ⇒
          reject(AuthenticationFailedRejection(CredentialsMissing, challengeFor(realm)))
      }
    }
}

object AuthDirectives extends AuthDirectives

import AuthDirectives._

object AuthService {
  def versionCheck =
    authenticateUserBasic(realm) { _ ⇒
      complete(versionCheckResponse)
    }

  private val versionCheckResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(`application/json`, "{}")
  )
}
