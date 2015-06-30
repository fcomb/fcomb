package io.fcomb.server

import io.fcomb.Db
import io.fcomb.utils.Config
import akka.actor.ActorSystem
import akka.kernel.Bootable
import akka.stream.ActorMaterializer
// import kamon.Kamon
import scala.util.{ Success, Failure }

class ApiKernel extends Bootable {
  // Kamon.start()

  implicit val system = ActorSystem("fcomb-server", Config.config)
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  def startup() = {
    (for {
      _ <- Db.migrate()
      _ <- HttpApiService.start(Config.config)
    } yield ()).onComplete {
      case Success(_) =>
      case Failure(e) =>
        throw e
    }

    // {
    //   import argonaut._, Argonaut._
    //   import scalaz._, Scalaz._

    //   case class A(a: Option[String], l: List[Int])

    //   implicit def ACodecJson: DecodeJson[A] =
    //     DecodeJson(c => for {
    //       a <- (c --\ "a").as[Option[String]]
    //       l <- (c --\ "l").as[Option[List[Int]]]
    //     } yield A(a, l.getOrElse(List.empty)))

    //   val a = """
    //   {
    //     "a": "wow"
    //   }
    //   """.decode[A]
    //   println(s"a: $a")
    // }
  }

  def shutdown() = {
    system.shutdown()
    // Kamon.shutdown()
  }
}
