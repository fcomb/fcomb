package io.fcomb.server

import io.fcomb.Db
import io.fcomb.utils.Config
import akka.actor.ActorSystem
import akka.kernel.Bootable
import akka.stream.ActorMaterializer
// import kamon.Kamon
import scala.util.{ Success, Failure }

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
      println(s"res: ${io.fcomb.persist.User.res}")
    case Failure(e) =>
      system.terminate()
    // Kamon.shutdown()
    // throw e
  }
}
