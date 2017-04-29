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

package io.fcomb.application

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.fcomb.application.server.Frontend
import io.fcomb.config.Configuration
import io.fcomb.Db
import io.fcomb.docker.distribution.server.{Api => DockerApi}
import io.fcomb.docker.distribution.services.{GarbageCollectorService, ImageBlobPushProcessor}
import io.fcomb.server.{Api, ApiHandlerConfig}
import io.fcomb.services.EventService
import pureconfig.error._
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Main extends App with LazyLogging {
  val config = ConfigFactory.load()

  implicit val sys = ActorSystem("fcomb", config)
  implicit val mat = ActorMaterializer()
  implicit val settings = Configuration.loadSettings(config) match {
    case Right(s) => s
    case Left(failures) =>
      val e = ConfigReaderException[Config](failures)
      logger.error(s"Configuration load failures: $failures")
      throw e
  }
  implicit val db               = Db(settings.jdbc)
  implicit val apiHandlerConfig = ApiHandlerConfig()

  import sys.dispatcher

  logger.info("Settings: {}", settings)

  val routes = Api.routes() ~ DockerApi.routes() ~ Frontend.routes

  (for {
    _ <- Db.migrate(settings.jdbc)
    _ <- server.HttpApiService.start(settings.api.httpPort, settings.api.interface, routes)
  } yield ()).onComplete {
    case Success(_) =>
      ImageBlobPushProcessor.startRegion(25.minutes)
      GarbageCollectorService.start()
      EventService.start()
    case Failure(e) =>
      logger.error(e.getMessage(), e.getCause())
      sys.terminate()
  }

  Await.result(sys.whenTerminated, Duration.Inf)
}
