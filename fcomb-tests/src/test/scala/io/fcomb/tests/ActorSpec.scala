package io.fcomb.tests

import io.fcomb.utils.Random
import akka.actor._
import akka.testkit._
import akka.stream._
import akka.stream.scaladsl._
import akka.http.scaladsl._
import akka.http.scaladsl.server._
import akka.util.ByteString
import org.scalatest._
import scala.io.{Source => ISource}
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

trait SpecHelpers {
  def getFixture(path: String) = {
    val is = getClass.getClassLoader.getResourceAsStream(s"fixtures/$path")
    ISource.fromInputStream(is).mkString
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

  def startFakeHttpServer(handler: Route)(f: Int => Future[Unit]): Unit =
    await {
      val port = Random.random.nextInt(50000) + 10000
      Http().bindAndHandle(handler, "localhost", port).flatMap { h =>
        f(port).andThen {
          case res => h.unbind().map(_ => res)
        }
      }
    }

  def source2ByteString(s: Source[ByteString, Any]): Future[ByteString] =
    s.runWith(Sink.fold(ByteString.empty)(_ ++ _))

  def source2String(s: Source[ByteString, Any]): Future[String] =
    source2ByteString(s).map(_.utf8String)
}
