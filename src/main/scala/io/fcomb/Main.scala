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

object Main extends App {
  private val logger = LoggerFactory.getLogger(this.getClass)
  // Kamon.start()

  val config = Config.config

  implicit val sys = ActorSystem(Config.actorSystemName, config)
  implicit val mat = ActorMaterializer()
  import sys.dispatcher

  val cluster = Cluster(sys)

  if (config.getList("akka.cluster.seed-nodes").isEmpty) {
    logger.info("Going to a single-node cluster mode")
    cluster.join(cluster.selfAddress)
  }

  Implicits.global(sys, mat)

  val interface = config.getString("rest-api.interface")
  val port = config.getInt("rest-api.port")

  import io.fcomb.services.docker.DockerApiClient
  import io.fcomb.services.docker.DockerApiClient._
  val dc = new DockerApiClient("coreos", 2375)
  // dc.getInfo().map { res =>
  //   println(s"res: $res")
  // }.recover {
  //   case e: Throwable => println(s"e: $e")
  // }
  // dc.getContainerProcesses("mongo").onComplete {
  //   case Success(res) => println(res)
  //   case Failure(e) =>
  //     e.printStackTrace()
  //     println(e)
  // }
  import akka.stream.scaladsl._
  import akka.util.ByteString
  import scala.concurrent.duration._

  dc.containerExport("mongo").onComplete {
    case Success(res) =>
      val s = akka.stream.io.SynchronousFileSink(new java.io.File("/tmp/export_test.tar"))
      s.runWith(res)
      println(res)
    case Failure(e) =>
      e.printStackTrace()
      println(e)
  }

  (for {
    _ <- Db.migrate()
    _ <- server.HttpApiService.start(port, interface, Routes())
  } yield ()).onComplete {
    case Success(_) =>
    // HttpProxy.start(config)
    case Failure(e) =>
      logger.error(e.getMessage(), e.getCause())
      try {
        // Kamon.shutdown()
        sys.terminate()
      } finally {
        System.exit(-1)
      }
  }

  Await.result(sys.whenTerminated, Duration.Inf)
}
