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
    var start = 0L
    var stop = 0L
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
    println("route trie: " + time(warmup) {
      var i = 0
      while (i < 1000000) {
        m.get("/user/about")
        i = i + 1
      }
    })
  }

  // {
  //   val vals = Map(
  //     "kek" -> 123,
  //     "g" -> 123,
  //     "vvv" -> 333,
  //     "sdsdsdsd" -> 34343,
  //     "dawdawdawdawdawdawdawdaw" -> 121212,
  //     "sdsadwdawdawdawdawdawd" -> 111,
  //     "sdawdawyei7batw7e t" -> 123,
  //     "2kek" -> 123,
  //     "g2" -> 123,
  //     "vv2v" -> 333,
  //     "sds2dsdsd" -> 34343,
  //     "dawdaw2dawdawdawdawdawdaw" -> 121212,
  //     "sdsa1dw1dawdawdawdawdawd" -> 111,
  //     "2323sdawd1awyei7batw7e t" -> 123,
  //     "k1ek" -> 123,
  //     "g1" -> 123,
  //     "vv1v" -> 333,
  //     "sds1dsdsd" -> 34343,
  //     "dawd1awdawdawdawdawdawdaw" -> 121212,
  //     "sdsad1wdawdawdawdawdawd" -> 111,
  //     "s1dawda1wyei7batw7e t" -> 123,
  //     "12kek" -> 123,
  //     "g12" -> 123,
  //     "vv12v" -> 333,
  //     "sds12dsdsd" -> 34343,
  //     "dawd1aw2dawdawdawdawdawdaw" -> 121212,
  //     "sdsa11dw1dawdawdawdawdawd" -> 111,
  //     "2323sd1awd1awyei7batw7e t" -> 123
  //   )

  //   println("scala Map: " + time(warmup) {
  //     val mm = Map(vals.toSeq: _*)
  //     var i = 0
  //     while (i < 100000) {
  //       mm.get("dawd1awdawdawdawdawdawdaw")
  //       i = i + 1
  //     }
  //   })

  //   import scala.collection.{ immutable, mutable }

  //   println("scala immutable HashMap: " + time(warmup) {
  //     val mm = immutable.HashMap(vals.toSeq: _*)
  //     var i = 0
  //     while (i < 100000) {
  //       mm.get("dawd1awdawdawdawdawdawdaw")
  //       i = i + 1
  //     }
  //   })

  //   println("scala mutable HashMap: " + time(warmup) {
  //     val mm = mutable.HashMap(vals.toSeq: _*)
  //     var i = 0
  //     while (i < 100000) {
  //       mm.get("dawd1awdawdawdawdawdawdaw")
  //       i = i + 1
  //     }
  //   })

  //   println("scala AnyRefMap: " + time(warmup) {
  //     val mm = mutable.AnyRefMap(vals.toSeq: _*)
  //     var i = 0
  //     while (i < 100000) {
  //       mm.get("dawd1awdawdawdawdawdawdaw")
  //       i = i + 1
  //     }
  //   })

  //   println("scala OpenHashMap: " + time(warmup) {
  //     val mm = mutable.OpenHashMap(vals.toSeq: _*)
  //     var i = 0
  //     while (i < 100000) {
  //       mm.get("dawd1awdawdawdawdawdawdaw")
  //       i = i + 1
  //     }
  //   })

  //   println("java HashMap: " + time(warmup) {
  //     val mm = new java.util.HashMap[String, Int]()
  //     vals.foreach { case (k, v) => mm.put(k, v) }
  //     var i = 0
  //     while (i < 100000) {
  //       mm.get("dawd1awdawdawdawdawdawdaw")
  //       i = i + 1
  //     }
  //   })
  // }

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
