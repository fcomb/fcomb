package io.fcomb

import akka.actor.{ActorSystem, Address}
import akka.cluster.Cluster
import akka.stream.ActorMaterializer
import io.fcomb.api.services.Routes
import io.fcomb.services.UserCertificateProcessor
import io.fcomb.services.node.{NodeJoinProcessor, NodeProcessor, UserNodesProcessor}
import io.fcomb.services.application.ApplicationProcessor
import io.fcomb.utils.{Config, Implicits}
import org.slf4j.LoggerFactory
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import java.net.InetAddress
// import kamon.Kamon

object Main extends App {
  private val logger = LoggerFactory.getLogger(this.getClass)

  // Kamon.start()

  implicit val sys = ActorSystem(Config.actorSystemName, Config.config)
  implicit val mat = ActorMaterializer()
  import sys.dispatcher

  val cluster = Cluster(sys)

  if (Config.config.getList("akka.cluster.seed-nodes").isEmpty) {
    logger.info("Going to a single-node cluster mode")
    cluster.join(cluster.selfAddress)
  }

  Implicits.global(sys, mat)

  val interface = Config.config.getString("rest-api.interface")
  val port = Config.config.getInt("rest-api.port")

  (for {
    _ ← Db.migrate()
    _ ← server.HttpApiService.start(port, interface, Routes())
  } yield ()).onComplete {
    case Success(_) ⇒
      UserCertificateProcessor.startRegion(5.minutes)
      NodeJoinProcessor.startRegion(5.minutes)
      NodeProcessor.startRegion(25.minutes)
      ApplicationProcessor.startRegion(1.hour)
      UserNodesProcessor.startRegion(1.day)
    // HttpProxy.start(config)
    // sys.actorOf(CertificateProcessor.props(), name = CertificateProcessor.actorName)
    // import scala.concurrent.duration._
    // import akka.pattern.ask
    // import akka.util.Timeout
    // import CertificateProcessor._
    // val certProc = CertificateProcessor.startRegion(5.minutes)
    // implicit val t = Timeout(5.seconds)
    // (1 to 2).foreach { i ⇒
    //   (certProc.ref ? EntityEnvelope(1, GenerateUserCertificates)).map { res ⇒
    //     println(s"$i#res: $res")
    //   }
    // }
    case Failure(e) ⇒
      logger.error(e.getMessage(), e.getCause())
      try {
        // Kamon.shutdown()
        sys.terminate()
      }
      finally {
        System.exit(-1)
      }
  }

  Await.result(sys.whenTerminated, Duration.Inf)
}
