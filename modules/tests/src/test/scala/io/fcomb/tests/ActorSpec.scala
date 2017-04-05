/*
 * Copyright 2017 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.tests

import akka.actor._
import akka.cluster.Cluster
import akka.http.scaladsl.server._
import akka.http.scaladsl._
import akka.stream.scaladsl._
import akka.stream._
import akka.testkit._
import akka.util.ByteString
import com.typesafe.config.Config
import io.fcomb.config.Configuration
import io.fcomb.utils.Random
import org.scalatest._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

trait SpecHelpers {
  def getFixture(path: String) = {
    val is = getClass.getClassLoader.getResourceAsStream(s"fixtures/$path")
    Stream.continually(is.read).takeWhile(_ != -1).map(_.toByte).toArray
  }

  def getFixtureAsString(path: String) =
    new String(getFixture(path))
}

trait FutureSpec {
  implicit val timeout = 5.seconds // TODO: move into config

  def await[T](f: Future[T]): T =
    Await.result(f, timeout)
}

private object ActorSystemSpec {
  protected lazy val config = Configuration.loadConfig()

  implicit lazy val system = ActorSystem("fcomb", config)
  implicit lazy val mat    = ActorMaterializer()
  implicit lazy val ec     = system.dispatcher
}

sealed trait ActorSystemSpec extends FutureSpec {
  protected val config: Config

  implicit val system: ActorSystem

  implicit lazy val mat = ActorSystemSpec.mat
  implicit lazy val ec  = system.dispatcher
}

abstract class ActorSpec
    extends TestKit(ActorSystemSpec.system)
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ActorSystemSpec
    with SpecHelpers {
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

trait ActorClusterSpec {
  implicit val system: ActorSystem

  val cluster = Cluster(system)

  if (system.settings.config.getList("akka.cluster.seed-nodes").isEmpty) {
    println("Going to a single-node cluster mode")
    cluster.join(cluster.selfAddress)
  }
}
