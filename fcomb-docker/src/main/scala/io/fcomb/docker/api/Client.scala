package io.fcomb.docker.api

import io.fcomb.docker.api.methods._
import io.fcomb.docker.api.methods.ContainerMethods._
import io.fcomb.docker.api.json.ContainerMethodsFormat._
import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.stream.io.Framing
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.MediaTypes.`application/x-tar`
import akka.http.scaladsl.model.headers.{UpgradeProtocol, Upgrade}
import akka.http.scaladsl.Http
import akka.util.ByteString
import scala.concurrent.Future
import scala.concurrent.duration._
import spray.json._
import spray.json.DefaultJsonProtocol._
import org.slf4j.LoggerFactory
import org.apache.commons.codec.binary.Base64
import java.nio.ByteOrder
import java.time.ZonedDateTime

object StdStream extends Enumeration {
  type StdStream = Value

  val In = Value(0)
  val Out = Value(1)
  val Err = Value(2)
}

final class Client(val host: String, val port: Int)(
  implicit
  val sys: ActorSystem,
  val mat: Materializer
)
  extends ApiConnection {

  import sys.dispatcher

  protected val logger = LoggerFactory.getLogger(this.getClass)

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
    val entity = requestJsonEntity(config)
    apiJsonRequest(HttpMethods.POST, "/containers/create", params, entity)
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
    streams: Set[StdStream.StdStream],
    stream: Boolean,
    since: Option[ZonedDateTime],
    showTimestamps: Boolean,
    tail: Option[Int],
    idleTimeout: Option[Duration]
  ) = {
    require(streams.nonEmpty, "Streams cannot be empty")
    val params = Map(
      "follow" -> stream.toString,
      "stdout" -> streams.contains(StdStream.Out).toString,
      "stderr" -> streams.contains(StdStream.Err).toString,
      "since" -> since.map(_.toEpochSecond).getOrElse(0L).toString,
      "timestamps" -> showTimestamps.toString,
      "tail" -> tail.map(_.toString).getOrElse("all")
    )
    apiRequestAsSource(
      HttpMethods.GET,
      s"/containers/$id/logs",
      queryParams = params,
      idleTimeout = idleTimeout
    ).map(_.entity.dataBytes)
  }

  def containerLogs(
    id: String,
    streams: Set[StdStream.StdStream],
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
    streams: Set[StdStream.StdStream],
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
      .map(_.entity.dataBytes)

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

  // TODO: add hijack tcp and ws
  // def containerAttachAsStream(
  //   id: String,
  //   streams: Set[StdStream.StdStream],
  //   idleTimeout: Duration,
  //   stdin: Option[Source[ByteString, Any]] = None
  // ) = {
  //   val params = Map(
  //     "stream" -> "true",
  //     "stdout" -> streams.contains(StdStream.Out).toString,
  //     "stderr" -> streams.contains(StdStream.Err).toString,
  //     "stdin" -> stdin.nonEmpty.toString
  //   )
  //   apiRequestAsSource(
  //     HttpMethods.POST,
  //     s"/containers/$id/attach",
  //     params,
  //     idleTimeout = Some(idleTimeout),
  //     upgradeProtocol = Some("tcp")
  //   ).flatMap { e =>
  //       println(e.isCloseDelimited(), e.isIndefiniteLength())
  //       // .via(Framing.lengthField(4, 0, Int.MaxValue, ByteOrder.BIG_ENDIAN))
  //       e.dataBytes.runForeach { bs =>
  //         println(s"bs: $bs")
  //         // print(bs.utf8String)
  //       }
  //     }
  // }

  /*
  import akka.stream.scaladsl._
  import akka.util.ByteString
  import scala.concurrent.Promise
  import akka.http.scaladsl._
  import akka.http.scaladsl.model._
  import akka.http.scaladsl.model.headers._

  val closeConnection = Promise[ByteString]()
  val source = Source(closeConnection.future).drop(1)
  val sink = Sink.foreach[ByteString] { bs =>
    println(s"sink: $bs, ${bs.utf8String}")
  }
  val pf = Flow.fromSinkAndSource(sink, source)
  val settings = akka.http.ClientConnectionSettings(sys)

  val name = "ubuntu_tty" // "mongo"
  val req = HttpRequest(
    HttpMethods.POST,
    s"/containers/$name/attach?stream=1&stdout=1&stderr=1&stdin=1",
    headers = List(Upgrade(List(UpgradeProtocol("tcp"))))
  )

  _root_.akka.http.HijackTcp.outgoingConnection("coreos", 2375, settings, req, pf)
    .onComplete(mr => s"main result: $mr")
  // _root_.akka.http.HijackTcp.wstest()

   */

  def containerWait(id: String) =
    apiJsonRequest(HttpMethods.POST, s"/containers/$id/wait")
      .map(_.convertTo[ContainerWaitResponse])

  def containerRemove(
    id: String,
    force: Boolean = false,
    withVolumes: Boolean = false
  ) = {
    val params = Map(
      "force" -> force.toString,
      "v" -> withVolumes.toString
    )
    apiRequestAsSource(HttpMethods.DELETE, s"/containers/$id", params)
      .map(_ => ())
  }

  def containerCopy(id: String, path: String) = {
    val entity = requestJsonEntity(CopyConfig(path))
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/copy", entity = entity)
      .map(_.entity.dataBytes)
  }

  private def parseContainerPathStat(headers: Seq[HttpHeader]) = {
    headers.find(_.name == "X-Docker-Container-Path-Stat").map { header =>
      val json = new String(Base64.decodeBase64(header.value))
      json.parseJson.convertTo[ContainerPathStat]
    }
  }

  def containerArchiveInformation(id: String, path: String) =
    apiRequestAsSource(HttpMethods.HEAD, s"/containers/$id/archive", Map("path" -> path))
      .map(res => parseContainerPathStat(res.headers))

  def containerArchive(id: String, path: String) =
    apiRequestAsSource(HttpMethods.GET, s"/containers/$id/archive", Map("path" -> path)).map { res =>
      (res.entity.dataBytes, parseContainerPathStat(res.headers))
    }

  def containerArchiveExtract(
    id: String,
    source: Source[ByteString, Any],
    path: String,
    withOverwrite: Boolean = false
  ) = {
    val params = Map(
      "path" -> path,
      "noOverwriteDirNonDir" -> (!withOverwrite).toString
    )
    val entity = HttpEntity(`application/x-tar`, source)
    apiRequestAsSource(HttpMethods.PUT, s"/containers/$id/archive", params, entity)
      .map(_ => ())
  }
}
