package io.fcomb.application

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import io.fcomb.Db
import io.fcomb.server.{Routes => ApiRoutes}
import io.fcomb.services.EventService
import io.fcomb.docker.distribution.server.{Routes => DockerDistributionRoutes}
import io.fcomb.docker.distribution.services.{GarbageCollectorService, ImageBlobPushProcessor}
import io.fcomb.utils.{Config, Implicits}
import org.slf4j.LoggerFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends App {
  private val logger = LoggerFactory.getLogger(this.getClass)

  implicit val sys = ActorSystem(Config.actorSystemName, Config.config)
  implicit val mat = ActorMaterializer()
  import sys.dispatcher

  val cluster = Cluster(sys)

  if (Config.config.getList("akka.cluster.seed-nodes").isEmpty) {
    logger.info("Going to a single-node cluster mode")
    cluster.join(cluster.selfAddress)
  }

  Implicits.global(sys, mat) // TODO: remove it with redis dependency

  val interface = Config.config.getString("rest-api.interface")
  val port      = Config.config.getInt("rest-api.port")

  val drInterface = Config.config.getString("docker.distribution.rest-api.interface")
  val drPort      = Config.config.getInt("docker.distribution.rest-api.port")

  (for {
    _ <- Db.migrate()
    _ <- server.HttpApiService.start(port, interface, ApiRoutes())
    _ <- server.HttpApiService.start(drPort, drInterface, DockerDistributionRoutes())
  } yield ()).onComplete {
    case Success(_) =>
      ImageBlobPushProcessor.startRegion(25.minutes)
      GarbageCollectorService.start()
      EventService.start()
    case Failure(e) =>
      logger.error(e.getMessage(), e.getCause())
      try {
        sys.terminate()
      } finally {
        System.exit(-1)
      }
  }

  Await.result(sys.whenTerminated, Duration.Inf)
}
