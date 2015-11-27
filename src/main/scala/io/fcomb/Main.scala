package io.fcomb

import akka.actor.{ActorSystem, Address}
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import io.fcomb.api.services.Routes
import io.fcomb.services.CombMethodProcessor
import io.fcomb.utils.{Config, Implicits}
import org.slf4j.LoggerFactory
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}
import java.net.InetAddress
// import kamon.Kamon

object Main extends App {
  private val logger = LoggerFactory.getLogger(this.getClass)

  // Kamon.start()

  implicit val sys = ActorSystem(Config.actorSystemName, Config.config)
  implicit val mat = ActorMaterializer()
  import sys.dispatcher

  val cluster = Cluster(sys)

  if (Config.config.getList("akka.cluster.seed-nodes").isEmpty) {
    logger.info("Going to a single-node cluster mode")
    cluster.join(cluster.selfAddress)
  }

  Implicits.global(sys, mat)

  val interface = Config.config.getString("rest-api.interface")
  val port = Config.config.getInt("rest-api.port")

  import akka.stream.scaladsl._
  import akka.stream.io._
  import akka.util.ByteString
  import scala.concurrent._
  import scala.concurrent.duration._
  import io.fcomb.docker.api.Client, Client._
  import io.fcomb.docker.api._
  import io.fcomb.docker.api.methods.ContainerMethods._
  import io.fcomb.docker.api.methods.MiscMethods._

  import io.fcomb.crypto.Tls
  import java.nio.file.Paths

  val ctx = Tls.context(
    Paths.get("/tmp/coreos/client.der"),
    Paths.get("/tmp/coreos/client.pem"),
    Some(Paths.get("/tmp/coreos/ca.pem"))
  )

  val dc = new Client("coreos", 2376, Some(ctx))
  // dc.ping().onComplete(println)
  dc.execCreate("ubuntu_tty", List("/bin/bash"), StdStream.all, false).flatMap { res =>
    val p = Promise[ByteString]()
    val source = Source.tick(1.second, 1.second, ByteString("ls\n")) ++
      Source(p.future).drop(1)
    val flow = Flow.fromSinkAndSource(Sink.foreach[StdStreamFrame.StdStreamFrame] {
      case (_, bs) =>
        println(bs.utf8String)
    }, source)
    println(s"res: $res")
    dc.execAttachAsStream(res.id, flow)
  }.onComplete(println)

  // (for {
  //   _ <- Db.migrate()
  //   _ <- server.HttpApiService.start(port, interface, Routes())
  // } yield ()).onComplete {
  //   case Success(_) =>
  //   // HttpProxy.start(config)
  //   case Failure(e) =>
  //     logger.error(e.getMessage(), e.getCause())
  //     try {
  //       // Kamon.shutdown()
  //       sys.terminate()
  //     } finally {
  //       System.exit(-1)
  //     }
  // }

  Await.result(sys.whenTerminated, Duration.Inf)
}
