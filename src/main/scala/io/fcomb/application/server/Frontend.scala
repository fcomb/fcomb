package io.fcomb.application.server

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object Frontend {
  def routes(): Route = {
    // format: OFF
    pathEndOrSingleSlash(get(getFromResource("public/index.html"))) ~
    get(getFromResourceDirectory("public"))
    // format: ON
  }
}
