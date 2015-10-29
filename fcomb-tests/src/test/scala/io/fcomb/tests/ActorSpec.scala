package io.fcomb.tests

import io.fcomb.utils.Random
import akka.actor._
import akka.testkit._
import akka.stream.ActorMaterializer
import akka.http.scaladsl._
import akka.http.scaladsl.server._
import org.scalatest._
import scala.io.Source
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait SpecHelpers {
  def getFixture(path: String) = {
    val is = getClass.getClassLoader.getResourceAsStream(s"fixtures/$path")
    Source.fromInputStream(is).mkString
  }
}

private object ActorSystemSpec {
  implicit lazy val system = ActorSystem("fcomb-tests")

  implicit lazy val mat = ActorMaterializer()

  implicit lazy val ec = system.dispatcher
}

sealed trait ActorSystemSpec {
  implicit val system: ActorSystem

  implicit lazy val mat = ActorSystemSpec.mat

  implicit lazy val ec = system.dispatcher
}

abstract class ActorSpec extends TestKit(ActorSystemSpec.system)
  with ImplicitSender with WordSpecLike with Matchers
  with BeforeAndAfterAll with ActorSystemSpec with SpecHelpers {

  implicit val timeout = 1.second // TODO: move into config

  def await[T](f: Future[T]): T =
    Await.result(f, timeout)

  def startFakeHttpServer(handler: Route)(f: Int => Future[_]): Unit =
    await {
      val port = Random.rand.nextInt(10000) + 1001
      Http().bindAndHandle(handler, "localhost", port).flatMap { h =>
        f(port).andThen {
          case res => h.unbind().map(_ => res)
        }
      }
    }
}
