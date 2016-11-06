/*
 * Copyright 2016 fcomb. <https://fcomb.io>
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

package io.fcomb.application

import akka.actor.ActorSystem
import akka.cluster.Cluster
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import io.fcomb.Db
import io.fcomb.application.server.Frontend
import io.fcomb.docker.distribution.server.{Api => DockerApi}
import io.fcomb.docker.distribution.services.{GarbageCollectorService, ImageBlobPushProcessor}
import io.fcomb.server.Api
import io.fcomb.services.{EmailService, EventService}
import io.fcomb.utils.Config
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends App with LazyLogging {
  implicit val sys = ActorSystem(Config.actorSystemName, Config.config)
  implicit val mat = ActorMaterializer()
  import sys.dispatcher

  val cluster = Cluster(sys)
  cluster.registerOnMemberRemoved {
    sys.registerOnTermination(System.exit(-1))
    sys.scheduler.scheduleOnce(10.seconds)(System.exit(-1))(sys.dispatcher)
    sys.terminate()
  }

  if (Config.config.getList("akka.cluster.seed-nodes").isEmpty) {
    logger.info("Going to a single-node cluster mode")
    cluster.join(cluster.selfAddress)
  }

  val interface = Config.config.getString("rest-api.interface")
  val port      = Config.config.getInt("rest-api.port")

  val routes = Api.routes ~ DockerApi.routes ~ Frontend.routes

  (for {
    _ <- Db.migrate()
    _ <- server.HttpApiService.start(port, interface, routes)
  } yield ()).onComplete {
    case Success(_) =>
      ImageBlobPushProcessor.startRegion(25.minutes)
      GarbageCollectorService.start()
      EventService.start()
      EmailService.start()
    case Failure(e) =>
      logger.error(e.getMessage(), e.getCause())
      sys.terminate()
  }

  Await.result(sys.whenTerminated, Duration.Inf)
}
