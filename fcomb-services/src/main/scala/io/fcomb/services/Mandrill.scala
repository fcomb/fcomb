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

import io.fcomb.utils.Config
import akka.actor.ActorSystem
import scala.concurrent.Future
import akka.stream.Materializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.stream.scaladsl.{Source, Sink}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.unmarshalling.Unmarshal
import io.circe.generic.auto._
import de.heikoseeberger.akkahttpcirce.CirceSupport._

object Mandrill {
  private[Mandrill] final case class SentStatus(
      email: Option[String],
      status: String,
      reject_reason: Option[String]
  )

  private[Mandrill] final case class Email(email: String)

  private[Mandrill] final case class Message(
      to: List[Email],
      html: String
  )

  private[Mandrill] final case class SendTemplate(
      key: String,
      template_name: String,
      template_content: List[String],
      message: Message
  )

  def sendTemplate(templateName: String, messageTo: List[String], messageHtml: String)(
      implicit sys: ActorSystem,
      mat: Materializer
  ): Future[List[SentStatus]] = {
    import sys.dispatcher

    val sendTemplate = SendTemplate(
        key = Config.mandrillKey,
        template_name = templateName,
        template_content = List(),
        message = Message(
            to = messageTo.distinct.map(Email),
            html = messageHtml.trim
        )
    )
    Source
      .fromFuture(Marshal(sendTemplate).to[RequestEntity])
      .map { entity ⇒
        HttpRequest(
            method = HttpMethods.POST,
            uri = "https://$hostname/api/1.0/messages/send-template.json",
            entity = entity
        )
      }
      .via(Http().outgoingConnection(hostname))
      .runWith(Sink.head)
      .flatMap(Unmarshal(_).to[List[SentStatus]])
  }

  private val hostname = "mandrillapp.com"
}
