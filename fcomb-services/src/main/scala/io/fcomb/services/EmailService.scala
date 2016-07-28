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
import io.fcomb.models.User
import io.fcomb.templates.HtmlTemplate
import io.fcomb.utils.Config
import org.apache.commons.mail._
import org.slf4j.LoggerFactory

object EmailService {

  import EmailServiceMessages._

  val actorName = "email-service"

  private var actorRef: ActorRef = _
  private lazy val logger = LoggerFactory.getLogger(getClass)

  // todo - Pinned Dispatcher?
  def start()(implicit system: ActorSystem, mat: Materializer): ActorRef = {
    if (actorRef eq null) {
      logger.info("Start " + actorName)
      actorRef = system.actorOf(props(), name = actorName)
    }
    actorRef
  }

  def sendTemplate(template: HtmlTemplate, user: User) =
    actorRef ! ResetPassword(template, user)

  def props()(implicit mat: Materializer) =
    Props(new EmailServiceActor())
}

private[this] sealed trait EmailServiceMessage

private[this] object EmailServiceMessages {
  final case class ResetPassword(template: HtmlTemplate, user: User) extends EmailServiceMessage
}

private[this] class EmailServiceActor(implicit mat: Materializer) extends Actor with ActorLogging {
  import EmailServiceMessages._

  def receive: Receive = {
    case msg: EmailServiceMessage =>
      msg match {
        case ResetPassword(template, user) =>
          sendTemplate(template, user)
        case _ =>
      }
  }

  private val config = Config.smtp

  private def sendTemplate(template: HtmlTemplate, user: User) = {
    try {
      val email = makeDefaultHtmlEmail()
      email.addTo(user.email, user.fullName.getOrElse(""))
      email.setSubject(template.subject)
      email.setHtmlMsg(template.toHtml)

      email.send()
    } catch {
      case e: EmailException =>
        log.error(e, e.getMessage)
    }
  }

  private def makeDefaultHtmlEmail() = {
    val email = new HtmlEmail()
    email.setHostName(config.getString("host"))
    email.setSmtpPort(config.getInt("port"))
    email.setAuthenticator(
      new DefaultAuthenticator(config.getString("user"), config.getString("password")))
    email.setSSLOnConnect(config.getBoolean("ssl"))
    email.setStartTLSEnabled(config.getBoolean("tls"))

    email.setFrom(config.getString("from"), config.getString("fromName"))
    email.setTextMsg("Your email client does not support HTML messages")

    email
  }
}
