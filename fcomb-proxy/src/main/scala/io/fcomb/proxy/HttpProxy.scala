package io.fcomb.proxy

import akka.actor._
import akka.http._
import akka.http.scaladsl._
import akka.http.scaladsl.server._
import akka.http.scaladsl.model.{ HttpResponse, StatusCodes, HttpMethod, HttpMethods }
import akka.http.scaladsl.model.headers.Host
import akka.stream._
import akka.stream.scaladsl._
import com.typesafe.config.Config
import _root_.io.fcomb.persist
import _root_.io.fcomb.models
import models.comb.MethodKind
import _root_.io.fcomb.trie.{ RouteTrie, RouteNode, RouteMethods }
import scala.concurrent.Future
import scala.collection.mutable.OpenHashMap

class HttpProxy(
    config: Config
)(
    implicit
    system:       ActorSystem,
    materializer: ActorMaterializer
) extends Actor with ActorLogging {
  import system.dispatcher

  @SerialVersionUID(1L)
  case class AddCombRoute(
    username: String,
    comb:     models.comb.Comb,
    route:    RouteNode[models.comb.CombMethod]
  )

  val interface = "0.0.0.0"
  val port = 5588

  val combsMap = OpenHashMap.empty[String, OpenHashMap[String, RouteNode[models.comb.CombMethod]]]

  def loadUsersWithCombs() = {
    persist.User.allWithCombs().map { l =>
      log.info(s"l: $l")
      combsMap ++= l.map {
        case (userId, username, combSlugs) =>
          val combsMap = OpenHashMap[String, RouteNode[models.comb.CombMethod]](
            combSlugs.map(s => (s, null)): _*
          )
          (username, combsMap)
      }
      log.info(s"combsMap: $combsMap")
    }
  }

  def convertMethodKind(method: MethodKind.MethodKind) =
    method match {
      case MethodKind.GET     => RouteMethods.GET
      case MethodKind.POST    => RouteMethods.POST
      case MethodKind.PUT     => RouteMethods.PUT
      case MethodKind.DELETE  => RouteMethods.DELETE
      case MethodKind.PATCH   => RouteMethods.PATCH
      case MethodKind.OPTIONS => RouteMethods.OPTIONS
      case MethodKind.HEAD    => RouteMethods.HEAD
    }

  def convertHttpMethod(method: HttpMethod) = method match {
    case HttpMethods.GET     => Some(RouteMethods.GET)
    case HttpMethods.POST    => Some(RouteMethods.POST)
    case HttpMethods.PUT     => Some(RouteMethods.PUT)
    case HttpMethods.DELETE  => Some(RouteMethods.DELETE)
    case HttpMethods.PATCH   => Some(RouteMethods.PATCH)
    case HttpMethods.OPTIONS => Some(RouteMethods.OPTIONS)
    case HttpMethods.HEAD    => Some(RouteMethods.HEAD)
    case _                   => None
  }

  def loadComb(username: String, slug: String) =
    persist.comb.Comb.findBySlugWithMethods(slug).map {
      case (comb, methods) =>
        val route = RouteTrie(methods.map { m =>
          m.uri -> (convertMethodKind(m.kind), m)
        }: _*)
        self ! AddCombRoute(username, comb, route)
        route
    }

  def splitUri(uri: String) = {
    var uriOffset: Int = 1

    def suburi() = {
      if (uriOffset < uri.length) {
        val nextPartPosition = uri.indexOf('/', uriOffset)
        val eof =
          if (nextPartPosition < 0) uri.length
          else nextPartPosition
        val res = uri.substring(uriOffset, eof)
        uriOffset += res.length + 1
        Some(res)
      } else None
    }

    (suburi(), suburi()) match {
      case (Some(username), Some(combSlug)) =>
        val combMethodUri =
          if (uriOffset < uri.length) uri.substring(uriOffset, uri.length)
          else "/"
        Some(username, combSlug, combMethodUri)
      case _ => None
    }
  }

  def handleCombRequest(
    ctx:    RequestContext,
    method: RouteMethods.RouteMethod,
    uri:    String,
    route:  RouteNode[models.comb.CombMethod]
  ) = {
    val (cm, options) = route.getRaw(method, uri)
    if (cm != null) {
      val proxyHost = cm.endpointHost
      val requestUri = ctx.request.getUri().host(proxyHost) // TODO: replace :param
      val proxyRequest = ctx.request
        .withHeaders(Host(proxyHost))
        .withUri(requestUri)
      val proxyPort = 80
      log.debug(s"proxyRequest: $proxyRequest")
      Source
        .single(proxyRequest)
        .via(Http().outgoingConnection(proxyHost, proxyPort))
        .runWith(Sink.head)
        .flatMap(ctx.complete(_))
    } else ctx.complete(HttpResponse(
      status = StatusCodes.NotFound,
      entity = "Resource not found"
    ))
  }

  val proxyHandler = Route { ctx =>
    val request = ctx.request
    convertHttpMethod(ctx.request.method) match {
      case Some(method) =>
        splitUri(request.uri.path.toString) match {
          case Some((username, combSlug, combUri)) =>
            log.info(s"username: $username, combSlug: $combSlug, combUri: $combUri")

            combsMap.get(username) match {
              case Some(combsMap) =>
                combsMap.get(combSlug) match {
                  case Some(route) =>
                    if (route == null) loadComb(username, combSlug)
                      .flatMap(handleCombRequest(ctx, method, combUri, _))
                    else handleCombRequest(ctx, method, combUri, route)
                  case None => ctx.complete(HttpResponse(
                    status = StatusCodes.NotFound,
                    entity = "Comb not found"
                  ))
                }
              case None => ctx.complete(HttpResponse(
                status = StatusCodes.NotFound,
                entity = "Username not found"
              ))
            }
          case _ => ctx.complete(HttpResponse(
            status = StatusCodes.BadRequest,
            entity = "Empty username or comb slug"
          ))
        }
      case _ => ctx.complete(HttpResponse(
        status = StatusCodes.BadRequest,
        entity = "Unsupported HTTP method"
      ))
    }
  }

  def receive = {
    case AddCombRoute(username, comb, route) =>
      log.info(s"AddCombRoute for comb: $comb")

      if (combsMap.contains(username) && combsMap(username).contains(comb.slug))
        combsMap(username)(comb.slug) = route
      else log.error(s"combsMap($username)(${comb.slug}) is not found")
    case m =>
      log.error(s"Unreceived message: $m")
  }

  def start() = for {
    _ <- loadUsersWithCombs
    _ <- Http().bindAndHandle(proxyHandler, interface, port)
  } yield ()

  start()
}

object HttpProxy {
  def start(config: Config)(implicit system: ActorSystem, materializer: ActorMaterializer) =
    system.actorOf(Props(new HttpProxy(config)), "http-proxy")
}
