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
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import io.fcomb.templates.HtmlTemplate
import io.fcomb.utils.Config.{smtp => config}
import org.apache.commons.mail._

object EmailService extends LazyLogging {
  val actorName = "email-service"

  private var actorRef: ActorRef = _

  // todo - Pinned Dispatcher?
  def start()(implicit system: ActorSystem, mat: Materializer): ActorRef = {
    if (actorRef eq null) {
      logger.info("Start {}", actorName)
      actorRef = system.actorOf(props(), name = actorName)
    }
    actorRef
  }

  def sendTemplate(template: HtmlTemplate, email: String, fullName: Option[String]) =
    actorRef ! EmailMessage(template, email, fullName)

  def props()(implicit mat: Materializer) =
    Props(new EmailServiceActor())
}

private[this] final case class EmailMessage(template: HtmlTemplate,
                                            email: String,
                                            fullName: Option[String])

private[this] class EmailServiceActor(implicit mat: Materializer) extends Actor with ActorLogging {
  def receive: Receive = {
    case EmailMessage(template, email, fullName) => sendTemplate(template, email, fullName)
  }

  private def sendTemplate(template: HtmlTemplate, email: String, fullName: Option[String]): Unit =
    try {
      val inst = emailInstance()
      inst.addTo(email, fullName.getOrElse(""))
      inst.setSubject(template.subject)
      inst.setHtmlMsg(template.toHtml)
      inst.send()
      ()
    } catch {
      case e: EmailException =>
        log.error(e, e.getMessage)
    }

  private def emailInstance() = {
    val inst = new HtmlEmail()
    inst.setHostName(config.getString("host"))
    inst.setSmtpPort(config.getInt("port"))
    inst.setAuthenticator(
      new DefaultAuthenticator(config.getString("user"), config.getString("password")))
    inst.setSSLOnConnect(config.getBoolean("ssl"))
    inst.setStartTLSEnabled(config.getBoolean("tls"))
    inst.setFrom(config.getString("from"), config.getString("fromName"))
    inst.setTextMsg("Your email client does not support HTML messages")
    inst
  }
}
