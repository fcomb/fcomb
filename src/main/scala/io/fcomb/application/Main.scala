package io.fcomb.application

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import io.fcomb.Db
import io.fcomb.docker.distribution.server.{Routes => DockerDistributionRoutes}
import io.fcomb.docker.distribution.services.{GarbageCollectorService, ImageBlobPushProcessor}
import io.fcomb.server.Routes
import io.fcomb.services.{EmailService, EventService}
import io.fcomb.utils.Config
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

  val interface = Config.config.getString("rest-api.interface")
  val port      = Config.config.getInt("rest-api.port")

  val routes = Routes() ~ DockerDistributionRoutes()

  (for {
    _ <- Db.migrate()
    _ <- server.HttpApiService.start(port, interface, routes)
  } yield ()).onComplete {
    case Success(_) =>
      ImageBlobPushProcessor.startRegion(25.minutes)
      GarbageCollectorService.start()
      EventService.start()
      EmailService.start()
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
