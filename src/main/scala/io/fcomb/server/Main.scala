package io.fcomb.server

import io.fcomb.Db
import io.fcomb.utils.Config
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
// import kamon.Kamon
import scala.util.{ Success, Failure }
import scala.language.existentials

object Main extends App {
  // Kamon.start()

  implicit val system = ActorSystem("fcomb-server", Config.config)
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  (for {
    _ <- Db.migrate()
    _ <- HttpApiService.start(Config.config)
  } yield ()).onComplete {
    case Success(_) =>
    case Failure(e) =>
      println(s"e: $e\n${e.getMessage()}\n${e.getStackTrace().mkString("\n\t")}")
      try {
        // Kamon.shutdown()
        system.terminate()
      } finally {
        System.exit(-1)
      }
  }
}
