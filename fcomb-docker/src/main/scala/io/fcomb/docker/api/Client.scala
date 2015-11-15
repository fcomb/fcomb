package io.fcomb.docker.api

import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.Http
import akka.util.ByteString
import scala.concurrent.Future
import scala.concurrent.duration._
import spray.json._
import spray.json.DefaultJsonProtocol._
import org.slf4j.LoggerFactory
import java.time.ZonedDateTime

object Client {
  sealed trait StdStream

  case object Stdout extends StdStream

  case object Stderr extends StdStream
}

class Client(host: String, port: Int)(implicit sys: ActorSystem, mat: Materializer) {
  import sys.dispatcher
  import Client._
  import Methods._
  import JsonFormats._

  private val logger = LoggerFactory.getLogger(this.getClass)

  import akka.http._

  // TODO: add TLS
  def connectionFlow(duration: Option[Duration] = None) = {
    val settings = duration.foldLeft(ClientConnectionSettings(sys)) {
      (s, d) => s.copy(idleTimeout = d)
    } match { // TODO: https://github.com/akka/akka/issues/16468
      case s => s.copy(parserSettings = s.parserSettings.copy(
        maxContentLength = Long.MaxValue
      ))
    }
    Http().outgoingConnection(host, port, settings = settings)
      .buffer(10, OverflowStrategy.backpressure)
  }

  private def uriWithQuery(uri: Uri, queryParams: Map[String, String]) =
    uri.withQuery(Uri.Query(queryParams.filter(_._2.nonEmpty)))

  private def apiRequestAsSource(
    method: HttpMethod,
    uri: Uri,
    queryParams: Map[String, String] = Map.empty,
    entity: RequestEntity = HttpEntity.Empty,
    idleTimeout: Option[Duration] = None
  ) = {
    Source
      .single(HttpRequest(
        uri = uriWithQuery(uri, queryParams),
        method = method,
        entity = entity
      ))
      .via(connectionFlow(idleTimeout))
      .runWith(Sink.head)
      .flatMap { res =>
        if (res.status.isSuccess()) Future.successful(res.entity)
        else {
          entity.dataBytes.runFold(new StringBuffer) { (acc, bs) =>
            acc.append(bs.utf8String)
          }.map { buf =>
            val msg = buf.toString()
            res.status.intValue() match {
              case 400 => throw new BadParameterException(msg)
              case 404 => throw new NoSuchContainerException(msg)
              case 406 => throw new ImpossibleToAttachException(msg)
              case 500 => throw new ServerErrorException(msg)
              case _ => throw new UnknownException(msg)
            }
          }
        }
      }
  }

  private def apiJsonRequestAsSource[T <: DockerApiRequest](
    method: HttpMethod,
    uri: Uri,
    queryParams: Map[String, String] = Map.empty,
    body: Option[T] = None
  )(implicit jw: JsonWriter[T]) = {
    val entity = body match {
      case Some(b) =>
        val req = b.toJson.compactPrint
        logger.debug(s"Docker API request: $req")
        HttpEntity(`application/json`, req)
      case _ => HttpEntity.Empty
    }
    apiRequestAsSource(method, uri, queryParams, entity).map { data =>
      data.dataBytes.map { res =>
        val s = res.utf8String
        logger.debug(s"Docker API response: $s")
        s.parseJson
      }
    }
  }

  private def apiJsonRequest[T <: DockerApiRequest](
    method: HttpMethod,
    uri: Uri,
    queryParams: Map[String, String] = Map.empty,
    body: Option[T] = None
  )(implicit jw: JsonWriter[T]) = {
    val entity = body match {
      case Some(b) =>
        val req = b.toJson.compactPrint
        logger.debug(s"Docker API request: $req")
        HttpEntity(`application/json`, req)
      case _ => HttpEntity.Empty
    }
    apiRequestAsSource(method, uri, queryParams, entity)
      .flatMap(_.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { res =>
        val s = res.utf8String
        logger.debug(s"Docker API response: $s")
        s.parseJson
      }
  }

  def information() =
    apiJsonRequest(HttpMethods.GET, "/info")
      .map(_.convertTo[Information])

  def version() =
    apiJsonRequest(HttpMethods.GET, "/version")
      .map(_.convertTo[Version])

  def containers(
    showAll: Boolean = true,
    showSize: Boolean = false,
    limit: Option[Int] = None,
    beforeId: Option[String] = None,
    sinceId: Option[String] = None
  ) = {
    val params = Map(
      "all" -> showAll.toString,
      "size" -> showSize.toString,
      "limit" -> limit.map(_.toString).getOrElse(""),
      "before" -> beforeId.getOrElse(""),
      "since" -> sinceId.getOrElse("")
    )
    apiJsonRequest(HttpMethods.GET, "/containers/json", params)
      .map(_.convertTo[List[ContainerItem]])
  }

  def containerCreate(
    config: ContainerCreate,
    name: Option[String] = None
  ) = {
    val params = Map(
      "name" -> name.getOrElse("")
    )
    apiJsonRequest(HttpMethods.POST, "/containers/create", params, Some(config))
      .map(_.convertTo[ContainerCreateResponse])
  }

  def containerInspect(
    id: String,
    showSize: Boolean = false
  ) = {
    val params = Map("size" -> showSize.toString)
    apiJsonRequest(HttpMethods.GET, s"/containers/$id/json", params)
      .map(_.convertTo[ContainerBase])
  }

  def containerProcesses(
    id: String,
    psArgs: Option[String] = None
  ) = {
    val params = Map("ps_args" -> psArgs.getOrElse(""))
    apiJsonRequest(HttpMethods.GET, s"/containers/$id/top", params)
      .map(_.convertTo[ContainerProcessList])
  }

  private def containerLogsAsSource(
    id: String,
    streams: Set[StdStream],
    stream: Boolean,
    since: Option[ZonedDateTime],
    showTimestamps: Boolean,
    tail: Option[Int],
    idleTimeout: Option[Duration]
  ) = {
    require(streams.nonEmpty, "Streams cannot be empty")
    val params = Map(
      "follow" -> stream.toString,
      "stdout" -> streams.contains(Stdout).toString,
      "stderr" -> streams.contains(Stderr).toString,
      "since" -> since.map(_.toEpochSecond).getOrElse(0L).toString,
      "timestamps" -> showTimestamps.toString,
      "tail" -> tail.map(_.toString).getOrElse("all")
    )
    apiRequestAsSource(
      HttpMethods.GET,
      s"/containers/$id/logs",
      queryParams = params,
      idleTimeout = idleTimeout
    ).map(_.dataBytes)
  }

  def containerLogs(
    id: String,
    streams: Set[StdStream],
    since: Option[ZonedDateTime] = None,
    showTimestamps: Boolean = false,
    tail: Option[Int] = None
  ) = containerLogsAsSource(
    id = id,
    streams = streams,
    stream = false,
    since = since,
    showTimestamps = showTimestamps,
    tail = tail,
    idleTimeout = None
  )

  def containerLogsAsStream(
    id: String,
    streams: Set[StdStream],
    idleTimeout: Duration,
    since: Option[ZonedDateTime] = None,
    showTimestamps: Boolean = false,
    tail: Option[Int] = None
  ) = containerLogsAsSource(
    id = id,
    streams = streams,
    stream = true,
    since = since,
    showTimestamps = showTimestamps,
    tail = tail,
    idleTimeout = Some(idleTimeout)
  )

  def containerChanges(id: String) =
    apiJsonRequest(HttpMethods.GET, s"/containers/$id/changes")
      .map(_.convertTo[ContainerChanges])

  def containerExport(id: String) =
    apiRequestAsSource(HttpMethods.GET, s"/containers/$id/export")
      .map(_.dataBytes)

  def containerStats(id: String) =
    apiJsonRequest(HttpMethods.GET, s"/containers/$id/stats", Map("stream" -> "false"))
      .map(_.convertTo[ContainerStats])

  def containerStatsAsStream(id: String) =
    apiJsonRequestAsSource(HttpMethods.GET, s"/containers/$id/stats", Map("stream" -> "true"))
      .map(_.map(_.convertTo[ContainerStats]))

  def containerResizeTty(id: String, width: Int, height: Int) = {
    val params = Map(
      "w" -> width.toString,
      "h" -> height.toString
    )
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/resize", params)
      .map(_ => ())
  }

  def containerStart(id: String) =
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/start")
      .map(_ => ())

  def containerStop(id: String, timeout: FiniteDuration) = {
    val params = Map(
      "t" -> timeout.toSeconds.toString()
    )
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/stop", params)
      .map(_ => ())
  }

  def containerKill(id: String, signal: Signal.Signal) = {
    val params = Map(
      "signal" -> signal.toString
    )
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/kill", params)
      .map(_ => ())
  }

  def containerRename(id: String, name: String) = {
    val params = Map(
      "name" -> name
    )
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/rename", params)
      .map(_ => ())
  }

  def containerPause(id: String) =
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/pause")
      .map(_ => ())

  def containerUnpause(id: String) =
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/unpause")
      .map(_ => ())


}
