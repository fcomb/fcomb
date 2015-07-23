package io.fcomb.proxy

import akka.actor._
import akka.stream._
import akka.stream.scaladsl._
import akka.http._
import akka.http.scaladsl._
import akka.http.scaladsl.server._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Host
import scala.concurrent.{ ExecutionContext, Future }

object HttpProxy extends App {
  implicit val system = ActorSystem("fcomb-proxy")
  implicit val materialazer = ActorMaterializer()

  import system.dispatcher

  println(s"start")

  val interface = "0.0.0.0"
  val port = 5588

  val proxyHandler = Route { ctx =>
    val request = ctx.request
    val proxyHost = "api"
    val proxyRequest = request
      .withHeaders(Host(proxyHost))
      .withUri(request.getUri().host(proxyHost))
    println(s"proxyRequest: $proxyRequest")
    Source
      .single(proxyRequest)
      .via(Http().outgoingConnection(proxyHost, 80))
      .runWith(Sink.head)
      .flatMap(ctx.complete(_))
  }
  Http().bindAndHandle(proxyHandler, interface, port)
}
