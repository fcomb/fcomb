package io.fcomb.docker.distribution.server

import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.model._

trait CommonDirectives {
  @inline
  def completeWithStatus(status: StatusCode): Route =
    complete(HttpResponse(status))

  @inline
  def completeNotFound(): Route =
    completeWithStatus(StatusCodes.NotFound)
}

object CommonDirectives extends CommonDirectives
