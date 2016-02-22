package io.fcomb.docker.registry.api.services

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import io.fcomb.api.services.ServiceRoute.Implicits._

object Routes {
  val apiVersion = "v2"

  def apply()(implicit sys: ActorSystem): Route = {
    import sys.dispatcher

    // format: OFF
    pathPrefix(apiVersion) {
      pathEndOrSingleSlash(get(complete("wow")))
    }
    // format: ON
  }
}
