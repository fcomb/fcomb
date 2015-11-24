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
  import io.fcomb.docker.api.Client, Client._
  import io.fcomb.docker.api.methods.ImageMethods._
  val dc = new Client("coreos", 2375)
  // val source = SynchronousFileSource(new java.io.File("/tmp/build.tar"))
  dc.imageSearch(
    "ubuntu"
  ).onComplete {
    case Success(res) =>
      // res.entity.dataBytes.runWith(Sink.foreach(bs => println(s"build: ${bs.utf8String}")))
      // val sink = SynchronousFileSink(new java.io.File("/tmp/etc.tar"))
      // res.runWith(sink).onComplete(println)
      println(res)
    case Failure(e) =>
      e.printStackTrace()
      println(e)
  }

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
