package io.fcomb.docker.distribution.api.services

import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import io.fcomb.api.services.ServiceRoute.Implicits._
import io.fcomb.docker.distribution.api.services.headers.`Docker-Distribution-Api-Version`

object Routes {
  val apiVersion = "v2"

  private val versionCheckResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(ContentTypes.`application/json`, "{}")
  )

  private val versionHeader = `Docker-Distribution-Api-Version`("2.0")

  private val defaultHeaders = List(versionHeader)

  def apply()(implicit sys: ActorSystem): Route = {
    import sys.dispatcher

    // format: OFF
    respondWithDefaultHeaders(defaultHeaders) {
      pathPrefix(apiVersion) {
        pathEndOrSingleSlash {
          get(complete(versionCheckResponse))
        } ~
        path(Segments) { segments =>
          { ctx: RequestContext =>
            println(s"uri: ${ctx.request.uri}")
            ctx.complete(HttpResponse(StatusCodes.NotFound))
          }
        }
      }
    }
    // format: ON
  }
}
