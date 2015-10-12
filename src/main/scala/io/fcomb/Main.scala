package io.fcomb

import akka.actor.{ActorSystem, Address}
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import io.fcomb.api.services.Routes
import io.fcomb.services.CombMethodProcessor
import io.fcomb.utils.{Config, Implicits}
import org.slf4j.LoggerFactory
import scala.concurrent.Await
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

  import akka.pattern.ask
  import akka.util.Timeout
  import scala.concurrent.duration._
  val regionTimeout = 2.minutes
  implicit val timeout = Timeout(5.seconds)
  val combMethodProcessorRegion = CombMethodProcessor.startRegion(regionTimeout)
  val req = CombMethodProcessor.EntityEnvelope(112, CombMethodProcessor.AddMethod(null))
  (combMethodProcessorRegion.ref ? req).onComplete(println)
  (combMethodProcessorRegion.ref ? CombMethodProcessor.EntityEnvelope(112, CombMethodProcessor.GetRouteTrie)).onComplete(println)
  combMethodProcessorRegion.ref ! CombMethodProcessor.EntityEnvelope(112, CombMethodProcessor.DestroyAll)

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
