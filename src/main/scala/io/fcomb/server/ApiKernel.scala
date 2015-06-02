package io.fcomb.server

import io.fcomb.Db
import io.fcomb.utils.Config
import akka.actor.ActorSystem
import akka.kernel.Bootable
import akka.stream.ActorFlowMaterializer
// import kamon.Kamon

class ApiKernel extends Bootable {
  // Kamon.start()

  implicit val system = ActorSystem("fcomb-server", Config.serverConfig)
  implicit val executor = system.dispatcher
  implicit val materializer = ActorFlowMaterializer()

  import system.dispatcher

  def startup() = {
    Db.migrate()

    HttpApiService.start(Config.serverConfig)
  }

  def shutdown() = {
    system.shutdown()
    // Kamon.shutdown()
  }
}
