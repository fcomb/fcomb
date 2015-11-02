package io.fcomb.services.docker

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.Http
import akka.util.ByteString
import spray.json._
import spray.json.DefaultJsonProtocol._
import org.slf4j.LoggerFactory

class DockerApiClient(host: String, port: Int)(implicit sys: ActorSystem, mat: Materializer) {
  import sys.dispatcher
  import DockerApiMessages._
  import DockerApiMessages.JsonProtocols._

  private val logger = LoggerFactory.getLogger(this.getClass)

  lazy val connectionFlow =
    Http().outgoingConnection(host, port) // TODO: add TLS

  private def apiRequest[T <: DockerApiRequest](
    method: HttpMethod,
    uri: Uri,
    body: Option[T] = None,
    queryParams: Map[String, String] = Map.empty
  )(implicit jw: JsonWriter[T]) = {
    val entity = body match {
      case Some(b) =>
        val req = b.toJson.compactPrint
        logger.debug(s"Docker API request: $req")
        HttpEntity(`application/json`, req)
      case _ => HttpEntity.Empty
    }
    Source
      .single(HttpRequest(
        uri = uri.withQuery(queryParams.filter(_._2.nonEmpty)),
        method = method,
        entity = entity
      ))
      .via(connectionFlow)
      .runWith(Sink.head)
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { res =>
        val s = res.utf8String
        logger.debug(s"Docker API response: $s")
        s.parseJson
      }
  }

  def getInformation() =
    apiRequest(HttpMethods.GET, "/info").map(_.convertTo[Information])

  def getVersion() =
    apiRequest(HttpMethods.GET, "/version").map(_.convertTo[Version])

  def getContainers(
    showAll: Boolean = true,
    showSize: Boolean = false,
    limit: Option[Int] = None,
    beforeId: Option[String] = None,
    sinceId: Option[String] = None
  ) = {
    val params = Map(
      "all" -> showAll.toString,
      "size" -> showSize.toString,
      "limit" -> limit.getOrElse("").toString,
      "before" -> beforeId.getOrElse(""),
      "since" -> sinceId.getOrElse("")
    ).filter(_._2.nonEmpty)
    apiRequest(HttpMethods.GET, "/containers/json", queryParams = params)
      .map(_.convertTo[List[ContainerItem]])
  }

  def createContainer(
    config: ContainerCreate,
    name: Option[String] = None
  ) = {
    val params = Map(
      "name" -> name.getOrElse("")
    )
    apiRequest(HttpMethods.POST, "/containers/create", Some(config), params)
      .map(_.convertTo[ContainerCreateResponse])
  }

  def getContainer(id: String) = {
    apiRequest(HttpMethods.GET, s"/containers/$id/json").map { json =>
      try {
        json.convertTo[ContainerBase]
      } catch {
        case e: Throwable => println(s"e: $e")
      }
    }
  }
}
