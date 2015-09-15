package io.fcomb

import io.fcomb.utils.Config
// import io.fcomb.proxy.HttpProxy
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
// import kamon.Kamon
import scala.util.{ Success, Failure }
import org.slf4j.LoggerFactory

object Main extends App {
  private val logger = LoggerFactory.getLogger(this.getClass)

  // Kamon.start()

  implicit val system = ActorSystem("fcomb-server", Config.config)
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  (for {
    _ <- Db.migrate()
    _ <- server.HttpApiService.start(Config.config)
  } yield ()).onComplete {
    case Success(_) =>
      // HttpProxy.start(Config.config)
    case Failure(e) =>
      logger.error(s"e: $e\n${e.getMessage()}\n${e.getStackTrace().mkString("\n\t")}")
      try {
        // Kamon.shutdown()
        system.terminate()
      } finally {
        System.exit(-1)
      }
  }
}
