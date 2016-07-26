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

package io.fcomb.services

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, HttpEntity}
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import io.circe.Encoder
import io.fcomb.models.EventDetails
import io.fcomb.models.docker.distribution.Image
import io.fcomb.persist.EventsRepo
import io.fcomb.persist.docker.distribution.ImageWebhooksRepo
import io.fcomb.json.models.Formats._
import org.slf4j.LoggerFactory

object EventService {
  import EventServiceMessages._

  val actorName = "event-service"

  private var actorRef: ActorRef = _
  private lazy val logger = LoggerFactory.getLogger(getClass)

  def start()(implicit system: ActorSystem, mat: Materializer): ActorRef = {
    if (actorRef eq null) {
      logger.info("Start event service")
      actorRef = system.actorOf(props(), name = actorName)
    }
    actorRef
  }

  def pushRepoEvent(img: Image, manifestId: Int, reference: String, createdByUserId: Int) = {
    val details = EventDetails.PushRepo(
      repoId = img.getId(),
      name = img.name,
      slug = img.slug,
      manifestId = manifestId,
      reference = reference
    )
    actorRef ! PushRepoEvent(details, createdByUserId)
  }

  def props()(implicit mat: Materializer) =
    Props(new EventServiceActor())
}

private[this] sealed trait EventServiceMessage

private[this] object EventServiceMessages {
  final case class PushRepoEvent(details: EventDetails.PushRepo, createdByUserId: Int)
      extends EventServiceMessage
}

private[this] class EventServiceActor(implicit mat: Materializer) extends Actor with ActorLogging {
  import context.dispatcher
  import context.system
  import EventServiceMessages._

  def receive: Receive = {
    case msg: EventServiceMessage =>
      msg match {
        case PushRepoEvent(details, createdByUserId) =>
          pushRepoEvent(details, createdByUserId)
        case _ =>
      }
  }

  def pushRepoEvent(details: EventDetails.PushRepo, createdByUserId: Int) = {
    EventsRepo.create(details, createdByUserId).flatMap { event =>
      val body   = Encoder[EventDetails].apply(details).noSpaces
      val entity = HttpEntity(`application/json`, body)
      ImageWebhooksRepo
        .findByImageIdAsStream(details.repoId)
        .mapAsyncUnordered(1) { webhook =>
          Http().singleRequest(HttpRequest(HttpMethods.POST, webhook.url, entity = entity))
        }
        .runWith(Sink.ignore)
    }
  }
}
