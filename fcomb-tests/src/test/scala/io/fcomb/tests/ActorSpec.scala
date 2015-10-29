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

abstract class ActorSpec extends TestKit(ActorSystem("fcomb-tests"))
  with ImplicitSender with WordSpecLike with Matchers
  with BeforeAndAfterAll with SpecHelpers {

  implicit lazy val mat = ActorMaterializer()

  implicit val ec = system.dispatcher

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

  override def afterAll(): Unit = {
    await(system.terminate())
    super.afterAll()
  }
}
