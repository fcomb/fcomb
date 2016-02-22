package io.fcomb.docker.distribution.api.services

import io.fcomb.api.services.ServiceRoute.Implicits._
import io.fcomb.docker.distribution.api.services.headers.`Docker-Distribution-Api-Version`
import akka.actor._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import scala.concurrent.duration._

object Routes {
  val apiVersion = "v2"

  private val versionCheckResponse = HttpResponse(
    status = StatusCodes.OK,
    entity = HttpEntity(ContentTypes.`application/json`, "{}")
  )

  private val versionHeader = `Docker-Distribution-Api-Version`("2.0")

  private val defaultHeaders = List(versionHeader)

  def apply()(implicit sys: ActorSystem, mat: Materializer): Route = {
    import sys.dispatcher

    // format: OFF
    respondWithDefaultHeaders(defaultHeaders) {
      pathPrefix(apiVersion) {
        pathEndOrSingleSlash {
          get {
            { ctx: RequestContext =>
              ctx.complete(ctx.request.entity.toStrict(1.second).map { entity =>
                println(s"v2 check: uri: ${ctx.request.uri}, headers: ${ctx.request.headers}, entity: $entity")
                versionCheckResponse
              })
            }
          }
        } ~
        path(RestPath) { _ =>
          pathEndOrSingleSlash {
            { ctx: RequestContext =>
              ctx.complete(ctx.request.entity.toStrict(1.second).map { entity =>
                println(s"unknown route: uri: ${ctx.request.uri}, headers: ${ctx.request.headers}, entity: $entity")
                HttpResponse(StatusCodes.NotFound)
              })
            }
          }
        }
      }
    }
    // format: ON
  }
}
