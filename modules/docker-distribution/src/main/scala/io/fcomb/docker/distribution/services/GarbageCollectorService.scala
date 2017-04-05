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

package io.fcomb.docker.distribution.services

import akka.actor._
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import io.fcomb.config.Settings
import io.fcomb.docker.distribution.utils.BlobFileUtils
import io.fcomb.persist.docker.distribution.{BlobFilesRepo, ImageBlobsRepo}
import io.fcomb.PostgresProfile.api.Database
import java.time.OffsetDateTime
import java.util.UUID
import scala.collection.mutable
import scala.concurrent.duration._
import scala.util.Failure

object GarbageCollectorService extends LazyLogging {
  val actorName = "garbage-collector"

  private var actorRef: ActorRef = _

  def start()(implicit system: ActorSystem,
              mat: Materializer,
              db: Database,
              settings: Settings): ActorRef = {
    if (actorRef eq null) {
      logger.info("Start garbage collector")
      actorRef = system.actorOf(props(), name = actorName)
    }
    actorRef
  }

  def props()(implicit mat: Materializer, db: Database, settings: Settings) =
    Props(new GarbageCollectorActor())
}

private[this] sealed trait GarbageCollectorEntity

private[this] object GarbageCollectorEntity {
  case object CheckOutdated extends GarbageCollectorEntity
  case object CheckDeleting extends GarbageCollectorEntity
}

private[this] class GarbageCollectorActor(implicit mat: Materializer,
                                          db: Database,
                                          settings: Settings)
    extends Actor
    with ActorLogging {
  import context.dispatcher
  import context.system
  import GarbageCollectorEntity._

  log.info("Outdated check interval {}", settings.gc.outdatedCheckInterval)
  system.scheduler.schedule(settings.gc.outdatedCheckInterval, settings.gc.outdatedCheckInterval) {
    self ! CheckOutdated
  }

  log.info("Deleting check interval {}", settings.gc.deletingCheckInterval)
  system.scheduler.schedule(settings.gc.deletingCheckInterval, settings.gc.deletingCheckInterval) {
    self ! CheckDeleting
  }

  private val stashed = new mutable.HashSet[GarbageCollectorEntity]()

  val busy: Receive = {
    case e: GarbageCollectorEntity =>
      stashed += e
      ()
  }

  val idle: Receive = {
    case e: GarbageCollectorEntity =>
      context.become(busy, false)
      val fut = e match {
        case CheckOutdated =>
          val until = OffsetDateTime.now().minusSeconds(settings.gc.outdatedPeriod.toSeconds)
          db.run(ImageBlobsRepo.destroyOutdatedUploads(until))
        case CheckDeleting => runDeleting()
      }
      fut.onComplete { res =>
        context.become(idle, false)
        stashed.foreach(self ! _)
        stashed.clear()

        res match {
          case Failure(e) => log.error(e, e.getMessage)
          case _          =>
        }
      }
  }

  def receive = idle

  private def runDeleting() =
    Source
      .fromPublisher(db.stream(BlobFilesRepo.findDeleting()))
      .mapAsyncUnordered(8) { bf =>
        val fut = bf.digest match {
          case Some(digest) => BlobFileUtils.destroyBlob(digest)
          case _            => BlobFileUtils.destroyUploadBlob(bf.uuid)
        }
        fut.map(_ => Right(bf.uuid)).recover { case _ => Left(bf.uuid) }
      }
      .groupedWithin(256, 1.second)
      .mapAsyncUnordered(1) { items =>
        val (successful, failed) = items.foldLeft((List.empty[UUID], List.empty[UUID])) {
          case ((sxs, fxs), Right(uuid)) => (uuid :: sxs, fxs)
          case ((sxs, fxs), Left(uuid))  => (sxs, uuid :: fxs)
        }
        val q = for {
          _ <- BlobFilesRepo.destroy(successful)
          _ <- BlobFilesRepo.updateRetryCount(failed)
        } yield ()
        db.run(q)
      }
      .runWith(Sink.ignore)
}
