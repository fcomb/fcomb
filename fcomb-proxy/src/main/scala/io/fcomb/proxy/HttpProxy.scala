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
    "/:kek" -> 100
  )
  println("!" * 25)
  println(m)

  import com.twitter.common.objectsize.ObjectSizeCalculator

  println(s"m.size: ${m.size}, ${ObjectSizeCalculator.getObjectSize(m)}")

  println(s"m.get(/user): ${m.get("/user")}")
  println(s"m.get(/user/12): ${m.get("/user/12")}")
  println(s"m.get(/user/about): ${m.get("/user/about")}")
  println(s"m.get(/ua/kek): ${m.get("/ua/kek")}")
  println(s"m.get(user): ${m.get("user")}")

  def time(warmup: Int)(block: => Any) = {
    for (i <- 1 to warmup) block // possibly trigger JIT on freq used methods in block
    var goodSample = false
    var start = 0L; var stop = 0L;
    var N = 10
    while (!goodSample) {
      start = System.currentTimeMillis
      for (i <- 1 to N) block
      stop = System.currentTimeMillis
      if (stop - start < 500) N = N * 4 // require at least half a second for a decent sample
      else goodSample = true
    }
    val perOp = (stop.toDouble - start.toDouble) / N
    perOp
  }

  val warmup = 10

  {
    println("scala Map: " + time(warmup) {
      var i = 0
      while (i < 100000) {
        m.get("/user/about")
        i = i + 1
      };
    })
  }

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
