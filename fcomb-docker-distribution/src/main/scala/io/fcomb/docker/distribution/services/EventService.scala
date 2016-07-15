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
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpMethods, HttpRequest, RequestEntity}
import akka.stream.Materializer
import cats.data.Validated
import de.heikoseeberger.akkahttpcirce.CirceSupport._
import io.circe.generic.auto._
import io.circe.syntax._
import io.fcomb.models.docker.distribution._
import io.fcomb.persist.docker.distribution.{ImageEventsRepo, ImageManifestsRepo, ImageWebhooksRepo, ImagesRepo}
import org.jose4j.base64url.Base64
import org.slf4j.LoggerFactory

object EventService {
  import EventServiceMessages._

  private val actorName = "event-service"

  private var actorRef: ActorRef = _
  private lazy val logger = LoggerFactory.getLogger(getClass)

  def start()(implicit system: ActorSystem, mat: Materializer): ActorRef = {
    if (actorRef eq null) {
      logger.info("Start event service")
      actorRef = system.actorOf(props(), name = actorName)
    }
    actorRef
  }

  def createUpsertEvent(manifestId: Int) =
    actorRef.tell(ImageUpserted(manifestId), null)

  def props()(implicit mat: Materializer) =
    Props(new EventServiceActor())
}

private[this] object EventServiceMessages {
  final case class ImageUpserted(manifestId: Int)
}

private[this] class EventServiceActor(implicit mat: Materializer) extends Actor with ActorLogging {

  import context.dispatcher
  import context.system
  import EventServiceMessages._

  override def receive: Receive = {
    case ImageUpserted(manifestId) => createUpsertEvent(manifestId)
  }

  def createUpsertEvent(manifestId: Int) = {
    ImageManifestsRepo.findById(manifestId).map {
      case Some(manifest) =>
        ImagesRepo.findById(manifest.imageId).map {
          case Some(image) =>
            val detailsJson = ImageEventDetails
              .Upserted(
                name = image.name,
                slug = image.slug,
                visibilityKind = image.visibilityKind,
                tags = manifest.tags,
                length = manifest.length
              )
              .asJson

            ImageEventsRepo
              .create(manifest.getId(),
                      ImageEventKind.Upserted,
                      Base64.encode(detailsJson.noSpaces.getBytes("utf-8")))
              .map {
                case Validated.Valid(imageEvent) =>
                  Marshal(detailsJson)
                    .to[RequestEntity]
                    .map(entity =>
                        ImageWebhooksRepo
                          .findByImageId(manifest.imageId)
                          .map(_.foreach(webhook =>
                                Http().singleRequest(HttpRequest(method = HttpMethods.POST,
                                                                 uri = webhook.url,
                                                                 entity = entity)))))

                  Some(imageEvent)
                case Validated.Invalid(e) => None
              }
          case None => None
        }
      case _ => None
    }
  }
}
