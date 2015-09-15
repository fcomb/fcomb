package io.fcomb

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import io.fcomb.api.services.Routes
import io.fcomb.utils.Config.config
import org.slf4j.LoggerFactory
import scala.util.{Failure, Success}

object Main extends App {
  private val logger = LoggerFactory.getLogger(this.getClass)
  // Kamon.start()

  implicit val sys = ActorSystem("fcomb-server", config)
  implicit val mat = ActorMaterializer()
  import sys.dispatcher

  val interface = config.getString("rest-api.interface")
  val port = config.getInt("rest-api.port")

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
}
