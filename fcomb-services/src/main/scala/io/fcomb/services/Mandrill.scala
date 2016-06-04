package io.fcomb.services

import io.fcomb.utils.Config
import akka.actor.ActorSystem
import akka.util.ByteString
import scala.concurrent.Future
import akka.stream.Materializer
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import spray.json._

object Mandrill {
  private[Mandrill] case class SentStatus(
    email:         Option[String],
    status:        String,
    reject_reason: Option[String]
  )

  private[Mandrill] case class Email(
    email: String
  )

  private[Mandrill] case class Message(
    to:   List[Email],
    html: String
  )

  private[Mandrill] case class SendTemplate(
    key:              String,
    template_name:    String,
    template_content: List[String],
    message:          Message
  )

  private[Mandrill] object JsonProtocol extends DefaultJsonProtocol {
    implicit val sentStatusFormat = jsonFormat3(SentStatus)
    implicit val emailFormat = jsonFormat1(Email)
    implicit val messageFormat = jsonFormat2(Message)
    implicit val sendTemplateFormat = jsonFormat4(SendTemplate)
  }
  import JsonProtocol._

  def sendTemplate(
    templateName: String,
    messageTo:    List[String],
    messageHtml:  String
  )(implicit sys: ActorSystem, mat: Materializer) = {
    import sys.dispatcher

    val entity = HttpEntity(
      contentType = ContentTypes.`application/json`,
      SendTemplate(
      key = Config.mandrillKey,
      template_name = templateName,
      template_content = List(),
      message = Message(
        to = messageTo.distinct.map(Email),
        html = messageHtml.trim
      )
    ).toJson.toString
    )
    val request = HttpRequest(
      method = HttpMethods.POST,
      uri = "https://mandrillapp.com/api/1.0/messages/send-template.json",
      entity = entity
    )
    val responseFuture: Future[String] = Http()
      .singleRequest(request)
      .flatMap(_.entity.dataBytes
        .runFold(ByteString.empty)(_ ++ _)
        .map(_.utf8String))
    responseFuture.map(_.parseJson.convertTo[List[SentStatus]])
  }
}
