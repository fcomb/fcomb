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
  val m = RouteTrie(
    "/" -> 0,
    "/user" -> 1,
    "/userify" -> 12345,
    "/user/about" -> 2,
    "/user/:id/xxx" -> 9123,
    "/user/:id" -> 222,
    "/useragent" -> 3,
    "/ua/kek" -> 4,
    "/user/g/h/n" -> 6666,
    "/f/*file" -> 11111,
    "/урл/на/DFSD©Δ§ß∞¢" -> 11111235,
    "/:kek" -> 100
  )
  println("!" * 25)
  println(m)

  println(s"m.size: ${m.size}")

  println(s"m.get(/user): ${m.get("/user")}")
  println(s"m.get(/user/12): ${m.get("/user/12")}")
  println(s"m.get(/user/about): ${m.get("/user/about")}")
  println(s"m.get(/ua/kek): ${m.get("/ua/kek")}")
  println(s"m.get(user): ${m.get("user")}")
  println(s"m.get(/урл/на/DFSD©Δ§ß∞¢): ${m.get("/урл/на/DFSD©Δ§ß∞¢")}")
  println(s"m.get(/f/на/DFSD©Δ§ß∞¢): ${m.get("/f/на/DFSD©Δ§ß∞¢")}")
  println(s"m.get(/no_url): ${m.get("/no_url")}")

  // implicit val system = ActorSystem("fcomb-proxy")
  // implicit val materialazer = ActorMaterializer()

  // import system.dispatcher

  // println(s"start")

  // val interface = "0.0.0.0"
  // val port = 5588

  // val proxyHandler = Route { ctx =>
  //   val request = ctx.request
  //   val proxyHost = "api"
  //   val proxyRequest = request
  //     .withHeaders(Host(proxyHost))
  //     .withUri(request.getUri().host(proxyHost))
  //   println(s"proxyRequest: $proxyRequest")
  //   Source
  //     .single(proxyRequest)
  //     .via(Http().outgoingConnection(proxyHost, 80))
  //     .runWith(Sink.head)
  //     .flatMap(ctx.complete(_))
  // }
  // Http().bindAndHandle(proxyHandler, interface, port)
}
