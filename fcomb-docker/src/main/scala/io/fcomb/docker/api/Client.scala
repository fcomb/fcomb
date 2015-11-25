package io.fcomb.docker.api

import ParamHelpers._
import io.fcomb.docker.api.methods._
import io.fcomb.docker.api.methods.ContainerMethods._
import io.fcomb.docker.api.methods.ImageMethods._
import io.fcomb.docker.api.methods.MiscMethods._
import io.fcomb.docker.api.json.ContainerMethodsFormat._
import io.fcomb.docker.api.json.ImageMethodsFormat._
import io.fcomb.docker.api.json.MiscMethodsFormat._
import akka.actor.ActorSystem
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.stream.io.Framing
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{UpgradeProtocol, Upgrade}
import akka.http.scaladsl.Http
import akka.util.ByteString
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.collection.immutable
import spray.json._
import spray.json.DefaultJsonProtocol._
import org.slf4j.LoggerFactory
import org.apache.commons.codec.binary.Base64
import java.nio.ByteOrder
import java.time.ZonedDateTime
import java.net.URL

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
    showAll: Boolean = false,
    showSize: Boolean = false,
    limit: Option[Int] = None,
    beforeId: Option[String] = None,
    sinceId: Option[String] = None,
    filters: Map[String, Set[String]] = Map.empty
  ) = {
    val params = Map(
      "all" -> showAll.toString,
      "size" -> showSize.toString,
      "limit" -> limit.toParam,
      "before" -> beforeId.toParam(),
      "since" -> sinceId.toParam(),
      "filters" -> filters.toJson.compactPrint
    )
    apiJsonRequest(HttpMethods.GET, "/containers/json", params)
      .map(_.convertTo[List[ContainerItem]])
  }

  def containerCreate(
    config: ContainerCreate,
    name: Option[String] = None
  ) = {
    val params = Map(
      "name" -> name.toParam()
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
    val params = Map("ps_args" -> psArgs.toParam())
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
      "since" -> since.toParamAsTimestamp(),
      "timestamps" -> showTimestamps.toString,
      "tail" -> tail.map(_.toString).toParam("all")
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
  // val sink = Sink.foreach[ByteString] { bs =>
  //   println(s"sink: $bs, ${bs.utf8String}")
  // }
  // val pf = Flow.fromSinkAndSource(sink, Source.tick(1.second, 1.second, ByteString("ls\r")) ++ source)
  // val settings = akka.http.ClientConnectionSettings(sys)

  // val name = "ubuntu_tty" // "mongo"
  // val req = HttpRequest(
  //   HttpMethods.POST,
  //   s"/containers/$name/attach?stream=1&stdout=1&stderr=1&stdin=1",
  //   headers = immutable.Seq(Upgrade(List(UpgradeProtocol("tcp"))))
  // )

  // _root_.akka.http.HijackTcp.outgoingConnection("localhost", 2376, settings, req, pf)
  // .onComplete(mr => s"main result: $mr")
  // _root_.akka.http.HijackTcp.wstest()

  import akka.stream.stage._
  class CloseStage extends PushStage[ByteString, ByteString] {
    def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective =
      ctx.push(elem)

    override def onUpstreamFinish(ctx: Context[ByteString]): TerminationDirective = {
      // println("onUpstreamFinish!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!")
      // ???
      ctx.fail(new Throwable("!!!!"))
    }
  }

  val s = Source.tick(1.second, 1.second, ByteString("ls\r")) ++ source
  val ff = Tcp()
    .outgoingConnection("localhost", 2376)
    .transform(() => new CloseStage())
    .runWith(s, Sink.foreach(println))
    ._2
  ff.onComplete(mr => s"main result: $mr")
  ff.recover {
    case e: Throwable =>
      println(s"e: $e")
      ???
  }
   */

  def containerWait(id: String) =
    apiJsonRequest(HttpMethods.POST, s"/containers/$id/wait")
      .map(_.convertTo[ContainerWaitResponse])

  def containerRemove(
    id: String,
    withForce: Boolean = false,
    withVolumes: Boolean = false
  ) = {
    val params = Map(
      "force" -> withForce.toString,
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
    headers.find(_.is("x-docker-container-path-stat")).map { header =>
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
    val entity = requestTarEntity(source)
    apiRequestAsSource(HttpMethods.PUT, s"/containers/$id/archive", params, entity)
      .map(_ => ())
  }

  def images(
    showAll: Boolean = false,
    filters: Map[String, Set[String]] = Map.empty,
    withName: Option[String] = None
  ) = {
    val params = Map(
      "all" -> showAll.toString,
      "filters" -> filters.toJson.compactPrint,
      "filter" -> withName.toParam()
    )
    apiJsonRequest(HttpMethods.GET, "/images/json", params)
      .map(_.convertTo[List[ImageItem]])
  }

  def imageBuild(
    source: Source[ByteString, Any],
    dockerfile: String = "Dockerfile",
    name: Option[String] = None,
    remoteUri: Option[String] = None,
    showBuildOutput: Boolean = true,
    withCache: Boolean = false,
    withPull: Boolean = true,
    removeMode: RemoveMode.RemoveMode = RemoveMode.Default,
    memoryLimit: Option[Long] = None,
    memorySwapLimit: Option[Long] = None,
    cpuShares: Option[Int] = None,
    cpusetCpus: Option[String] = None,
    cpuPeriod: Option[Long] = None,
    cpuQuota: Option[Long] = None,
    registryConfig: Option[RegistryConfig] = None
  ) = {
    val params = Map(
      "dockerfile" -> dockerfile,
      "t" -> name.toParam(),
      "remote" -> remoteUri.toParam(),
      "q" -> (!showBuildOutput).toString,
      "nocache" -> (!withCache).toString,
      "pull" -> withPull.toString,
      "memory" -> memoryLimit.toParam(),
      "memswap" -> memorySwapLimit.toMemorySwapParam(),
      "cpushares" -> cpuShares.toParam(),
      "cpusetcpus" -> cpusetCpus.toParam(),
      "cpuperiod" -> cpuPeriod.toParam(),
      "cpuquota" -> cpuQuota.toParam()
    ) ++ RemoveMode.mapToParams(removeMode)
    val entity = requestTarEntity(source)
    val headers = RegistryConfig.mapToHeaders(registryConfig)
    apiRequestAsSource(HttpMethods.POST, s"/build", params, entity, headers = headers)
    // TODO: parse json events
  }

  def imagePull(
    name: String,
    repositoryName: Option[String] = None,
    tag: Option[String] = None,
    registry: Option[Registry] = None,
    registryAuth: Option[AuthConfig] = None
  ) = {
    val params = Map(
      "fromImage" -> name,
      "tag" -> tag.toParam(),
      "repo" -> repositoryName.toParam(),
      "registry" -> registry.toParam()
    )
    val headers = AuthConfig.mapToHeaders(registryAuth)
    apiRequestAsSource(HttpMethods.POST, s"/images/create", params, headers = headers)
    // TODO: parse json events
  }

  def imagePullWithUrl(
    url: URL,
    repositoryName: Option[String] = None,
    tag: Option[String] = None,
    registry: Option[Registry] = None,
    registryAuth: Option[AuthConfig] = None
  ) = {
    val params = Map(
      "fromSrc" -> url.toString,
      "tag" -> tag.toParam(),
      "repo" -> repositoryName.toParam(),
      "registry" -> registry.toParam()
    )
    val headers = AuthConfig.mapToHeaders(registryAuth)
    apiRequestAsSource(HttpMethods.POST, s"/images/create", params, headers = headers)
    // TODO: parse json events
  }

  def imagePullWithStream(
    source: Source[ByteString, Any],
    repositoryName: Option[String] = None,
    tag: Option[String] = None,
    registry: Option[Registry] = None,
    registryAuth: Option[AuthConfig] = None
  ) = {
    val params = Map(
      "fromSrc" -> "-",
      "tag" -> tag.toParam(),
      "repo" -> repositoryName.toParam(),
      "registry" -> registry.toParam()
    )
    val entity = requestTarEntity(source)
    val headers = AuthConfig.mapToHeaders(registryAuth)
    apiRequestAsSource(HttpMethods.POST, "/images/create", params, entity, headers = headers)
    // TODO: parse json events
  }

  def imageInspect(id: String) =
    apiJsonRequest(HttpMethods.GET, s"/images/$id/json")
      .map(_.convertTo[ImageInspect])

  def imageHistory(id: String) =
    apiJsonRequest(HttpMethods.GET, s"/images/$id/history")
      .map(_.convertTo[List[ImageHistory]])

  def imagePush(
    id: String,
    tag: Option[String] = None,
    registry: Option[Registry] = None,
    registryAuth: Option[AuthConfig] = None
  ) = {
    val params = Map("tag" -> tag.toParam())
    val headers = AuthConfig.mapToHeaders(registryAuth)
    val name = registry match {
      case Some(r) => s"${r.toParam}/$id"
      case None => id
    }
    apiRequestAsSource(HttpMethods.POST, s"/images/$name/push", params, headers = headers)
    // TODO: parse json events
  }

  def imageTag(
    id: String,
    repositoryName: String,
    tag: Option[String] = None,
    withForce: Boolean = false
  ) = {
    val params = Map(
      "tag" -> tag.toParam(),
      "repo" -> repositoryName,
      "force" -> withForce.toString
    )
    apiRequestAsSource(HttpMethods.POST, s"/images/$id/tag", params)
      .map(_ => ())
  }

  def imageRemove(
    id: String,
    withPrune: Boolean = true,
    withForce: Boolean = false
  ) = {
    val params = Map(
      "noprune" -> (!withPrune).toString(),
      "force" -> withForce.toString()
    )
    apiRequestAsSource(HttpMethods.DELETE, s"/images/$id", params)
      .map(_ => ())
  }

  def imageSearch(term: String) =
    apiJsonRequest(HttpMethods.GET, "/images/search", Map("term" -> term))
      .map(_.convertTo[List[ImageSearchResult]])

  def imageCommit(
    containerId: String,
    repositoryName: Option[String] = None,
    tag: Option[String] = None,
    comment: Option[String] = None,
    author: Option[String] = None,
    withPause: Boolean = false,
    changes: List[String] = List.empty,
    config: Option[RunConfig] = None
  ) = {
    val params = Map(
      "container" -> containerId,
      "repo" -> repositoryName.toParam(),
      "tag" -> tag.toParam(),
      "comment" -> comment.toParam(),
      "author" -> author.toParam(),
      "pause" -> withPause.toString,
      "changes" -> changes.mkString("\n")
    )
    val entity = config match {
      case Some(c) => requestJsonEntity(c)
      case None => HttpEntity.Empty
    }
    apiJsonRequest(HttpMethods.POST, "/commit", params, entity)
      .map(_.convertTo[ContainerCommitResponse])
  }

  def auth(config: AuthConfig) =
    apiRequestAsSource(HttpMethods.POST, "/auth", entity = requestJsonEntity(config))
      .map(_ => ())

  def ping() =
    apiRequestAsSource(HttpMethods.GET, "/_ping")
      .map(_ => ())

  def eventsAsStream(
    since: Option[ZonedDateTime] = None,
    until: Option[ZonedDateTime] = None,
    filters: EventsFilter = Map.empty
  ) = {
    val params = Map(
      "since" -> since.toParamAsTimestamp(),
      "until" -> since.toParamAsTimestamp(),
      "filters" -> EventsFitler.mapToParam(filters)
    )
    apiRequestAsSource(HttpMethods.GET, "/events", params)
    // TODO: map events
  }
}
