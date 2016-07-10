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

package io.fcomb.docker.distribution.services

import akka.actor._
import akka.stream.Materializer
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.persist.docker.distribution.ImageBlobsRepo
import io.fcomb.utils.Config.docker.distribution.gc
import java.time.ZonedDateTime
import org.slf4j.LoggerFactory
import scala.collection.mutable
import scala.concurrent.duration._

object GarbageCollectorService {
  val actorName = "garbage-collector"

  private var actorRef: ActorRef = _
  private lazy val logger = LoggerFactory.getLogger(getClass)

  def start()(implicit system: ActorSystem, mat: Materializer): ActorRef = {
    if (actorRef eq null) {
      logger.info("Start garbage collector")
      actorRef = system.actorOf(props(), name = actorName)
    }
    actorRef
  }

  def props()(implicit mat: Materializer) =
    Props(new GarbageCollectorActor())
}

private[this] sealed trait GarbageCollectorEntity

private[this] object GarbageCollectorEntity {
  final case object CheckOutdated extends GarbageCollectorEntity
  final case object CheckDeleting extends GarbageCollectorEntity
}

private[this] class GarbageCollectorActor(implicit mat: Materializer)
    extends Actor
    with ActorLogging {
  import context.dispatcher
  import context.system
  import GarbageCollectorEntity._

  log.info("Outdated check interval {}", gc.outdatedCheckInterval)
  system.scheduler.schedule(1.minute, gc.outdatedCheckInterval)(self ! CheckOutdated)

  log.info("Deleting check interval {}", gc.deletingCheckInterval)
  system.scheduler.schedule(1.second, gc.deletingCheckInterval)(self ! CheckDeleting)

  private val stashed = new mutable.HashSet[GarbageCollectorEntity]()

  val busy: Receive = {
    case e: GarbageCollectorEntity => stashed += e
  }

  val idle: Receive = {
    case e: GarbageCollectorEntity =>
      context.become(busy, false)
      (e match {
        case CheckOutdated =>
          val until = ZonedDateTime.now().minus(gc.outdatedPeriod)
          ImageBlobsRepo.destroyOutdatedUploads(until)
        case CheckDeleting => ???
      }).onComplete { _ =>
        context.become(idle, false)
        stashed.foreach(self ! _)
        stashed.clear()
      }
  }

  def receive = idle
}
