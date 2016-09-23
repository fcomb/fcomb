package io.fcomb.application.server

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route

object Frontend {
  val routes: Route = {
    // format: OFF
    encodeResponse {
      pathEndOrSingleSlash(get(getFromResource("public/index.html"))) ~
      get(getFromResourceDirectory("public"))
    }
    // format: ON
  }
}
