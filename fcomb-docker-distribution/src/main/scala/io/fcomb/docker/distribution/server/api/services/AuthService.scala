package io.fcomb.docker.distribution.server.api.services

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._

object AuthService {
  def versionCheck() =
    complete(versionCheckResponse)

  private val versionCheckResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(ContentTypes.`application/json`, "{}")
  )
}
