package io.fcomb

import akka.actor.{ActorSystem, Address}
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import io.fcomb.api.services.Routes
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

  {
    import io.fcomb.crypto._
    import java.nio.file._

    val (caCert, caKey) = Certificate.generateRootAuthority("", "", "", "", "", "")
    Files.write(Paths.get("/tmp/ca.pem"), Certificate.toPem(caCert).getBytes)
    Files.write(Paths.get("/tmp/ca-key.pem"), Certificate.toPem(caKey, Some("password")).getBytes)

    val (clientCert, clientKey) = Certificate.generateClient(caCert, caKey)
    Files.write(Paths.get("/tmp/client.pem"), Certificate.toPem(clientCert).getBytes)
    Files.write(Paths.get("/tmp/client-key.pem"), Certificate.toPem(clientKey, Some("password")).getBytes)

    println("done")
  }

  // val cluster = Cluster(sys)

  // if (Config.config.getList("akka.cluster.seed-nodes").isEmpty) {
  //   logger.info("Going to a single-node cluster mode")
  //   cluster.join(cluster.selfAddress)
  // }

  // Implicits.global(sys, mat)

  // val interface = Config.config.getString("rest-api.interface")
  // val port = Config.config.getInt("rest-api.port")

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
