package io.fcomb.docker.distribution.server.api

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.server.Directives._
import io.fcomb.docker.distribution.server.AuthenticationDirectives._

object AuthenticationHandler {
  def versionCheck =
    authenticateUserBasic { _ ⇒
      complete(versionCheckResponse)
    }

  private val versionCheckResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(`application/json`, "{}")
  )
}
