package io.fcomb.services.docker

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.util.ByteString
import spray.json._

class DockerClient(host: String, port: Int)(implicit sys: ActorSystem, mat: Materializer) {
  import sys.dispatcher
  import DockerApiMessages._
  import DockerApiMessages.JsonProtocols._

  lazy val connectionFlow =
    Http().outgoingConnection(host, port) // TODO: add TLS

  private def apiRequest(uri: Uri) =
    Source
      .single(HttpRequest(uri = uri))
      .via(connectionFlow)
      .runWith(Sink.head)
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map(_.utf8String.parseJson)

  def getInfo() = {
    apiRequest("/info").map { json =>
      println(s"DockerApiInfo: ${json.convertTo[DockerApiInfo]}")
    }
  }

  def getContainers(
    all: Boolean = true,
    size: Option[Int] = None,
    before: Option[String] = None
  ) = {
    val params = Map(
      "all" -> all.toString,
      "size" -> size.getOrElse("").toString,
      "before" -> before.getOrElse("")
    ).filter(_._2.nonEmpty)
    val uri = Uri("/containers/json").withQuery(params)
    apiRequest(uri).map { json =>
      println(s"containers: ${json.convertTo[List[DockerApiContainerItem]]}")
    }
  }
}
