package io.fcomb.docker.api

import ParamHelpers._
import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Upgrade, UpgradeProtocol}
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.util.ByteString
import io.fcomb.docker.api.json.ContainerMethodsFormat._
import io.fcomb.docker.api.json.ImageMethodsFormat._
import io.fcomb.docker.api.json.MiscMethodsFormat._
import io.fcomb.docker.api.methods.ContainerMethods._
import io.fcomb.docker.api.methods.ImageMethods._
import io.fcomb.docker.api.methods.MiscMethods._
import java.net.URL
import java.time.ZonedDateTime
import javax.net.ssl.SSLContext
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import scala.collection.immutable
import scala.concurrent.duration._
import spray.json._
import spray.json.DefaultJsonProtocol._

final class Client(
  val hostname:   String,
  val port:       Int,
  val sslContext: Option[SSLContext] = None
)(
  implicit
  val sys: ActorSystem,
  val mat: Materializer
)
    extends ApiConnection {

  import sys.dispatcher

  protected val logger = LoggerFactory.getLogger(this.getClass)

  val defaultIdleTimeout = 1.hour

  def information() =
    apiJsonRequest(HttpMethods.GET, "/info")
      .map(_.convertTo[Information])

  def version() =
    apiJsonRequest(HttpMethods.GET, "/version")
      .map(_.convertTo[Version])

  def containers(
    showAll:  Boolean                  = false,
    showSize: Boolean                  = false,
    limit:    Option[Int]              = None,
    beforeId: Option[String]           = None,
    sinceId:  Option[String]           = None,
    filters:  Map[String, Set[String]] = Map.empty
  ) = {
    val params = Map(
      "all" → showAll.toString,
      "size" → showSize.toString,
      "limit" → limit.toParam,
      "before" → beforeId.toParam(),
      "since" → sinceId.toParam(),
      "filters" → filters.toJson.compactPrint
    )
    apiJsonRequest(HttpMethods.GET, "/containers/json", params)
      .map(_.convertTo[List[ContainerItem]])
  }

  def containerCreate(
    config: ContainerCreate,
    name:   Option[String]  = None
  ) = {
    val params = Map(
      "name" → name.toParam()
    )
    val entity = requestJsonEntity(config)
    apiJsonRequest(HttpMethods.POST, "/containers/create", params, entity)
      .map(_.convertTo[ContainerCreateResponse])
  }

  def containerInspect(
    id:       String,
    showSize: Boolean = false
  ) = {
    val params = Map("size" → showSize.toString)
    apiJsonRequest(HttpMethods.GET, s"/containers/$id/json", params)
      .map(_.convertTo[ContainerBase])
  }

  def containerProcesses(
    id:     String,
    psArgs: Option[String] = None
  ) = {
    val params = Map("ps_args" → psArgs.toParam())
    apiJsonRequest(HttpMethods.GET, s"/containers/$id/top", params)
      .map(_.convertTo[ContainerProcessList])
  }

  private def containerLogsAsSource(
    id:             String,
    streams:        Set[StdStream.StdStream],
    stream:         Boolean,
    since:          Option[ZonedDateTime],
    showTimestamps: Boolean,
    tail:           Option[Int],
    idleTimeout:    Option[FiniteDuration]
  ) = {
    require(streams.nonEmpty, "Streams cannot be empty")
    val params = Map(
      "follow" → stream.toString,
      "stdout" → streams.contains(StdStream.Out).toString,
      "stderr" → streams.contains(StdStream.Err).toString,
      "since" → since.toParamAsTimestamp(),
      "timestamps" → showTimestamps.toString,
      "tail" → tail.map(_.toString).toParam("all")
    )
    apiRequestAsSource(
      HttpMethods.GET,
      s"/containers/$id/logs",
      queryParams = params,
      idleTimeout = idleTimeout
    ).map(_._1)
  }

  def containerLogs(
    id:             String,
    streams:        Set[StdStream.StdStream],
    since:          Option[ZonedDateTime]    = None,
    showTimestamps: Boolean                  = false,
    tail:           Option[Int]              = None
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
    id:             String,
    streams:        Set[StdStream.StdStream],
    since:          Option[ZonedDateTime]    = None,
    showTimestamps: Boolean                  = false,
    tail:           Option[Int]              = None,
    idleTimeout:    FiniteDuration           = defaultIdleTimeout
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
      .map(_._1)

  def containerStats(id: String) =
    apiJsonRequest(HttpMethods.GET, s"/containers/$id/stats", Map("stream" → "false"))
      .map(_.convertTo[ContainerStats])

  def containerStatsAsStream(
    id:          String,
    idleTimeout: FiniteDuration = defaultIdleTimeout
  ) =
    apiJsonRequestAsSource(
      HttpMethods.GET,
      s"/containers/$id/stats",
      Map("stream" → "true"),
      idleTimeout = Some(idleTimeout)
    ).map(_.map(_.convertTo[ContainerStats]))

  def containerResizeTty(id: String, width: Int, height: Int) = {
    val params = Map(
      "w" → width.toString,
      "h" → height.toString
    )
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/resize", params)
      .map(_ ⇒ ())
  }

  def containerStart(id: String) =
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/start")
      .map(_ ⇒ ())

  def containerStop(id: String, timeout: FiniteDuration) = {
    val params = Map(
      "t" → timeout.toSeconds.toString()
    )
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/stop", params)
      .map(_ ⇒ ())
  }

  def containerKill(id: String, signal: Signal.Signal) = {
    val params = Map(
      "signal" → signal.toString
    )
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/kill", params)
      .map(_ ⇒ ())
  }

  def containerRename(id: String, name: String) = {
    val params = Map(
      "name" → name
    )
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/rename", params)
      .map(_ ⇒ ())
  }

  def containerPause(id: String) =
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/pause")
      .map(_ ⇒ ())

  def containerUnpause(id: String) =
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/unpause")
      .map(_ ⇒ ())

  private val upgradeHeaders =
    immutable.Seq(Upgrade(List(UpgradeProtocol("tcp"))))

  private lazy val stdStreamFrameCodec =
    StdStreamFrame.codec(clientConnectionSettings.parserSettings.maxChunkSize).reversed

  private def containerAttachAsSource(
    id:          String,
    streams:     Set[StdStream.StdStream],
    idleTimeout: FiniteDuration
  ) = {
    val params = Map(
      "stream" → "true",
      "stdout" → streams.contains(StdStream.Out).toString,
      "stderr" → streams.contains(StdStream.Err).toString,
      "stdin" → streams.contains(StdStream.In).toString
    )
    val req = HttpRequest(
      method = HttpMethods.POST,
      uri = uriWithQuery(s"/containers/$id/attach", params),
      headers = upgradeHeaders
    )
    hijackConnectionFlow(req, idleTimeout)
  }

  def containerAttachAsStream(
    id:          String,
    streams:     Set[StdStream.StdStream],
    flow:        Flow[StdStreamFrame.StdStreamFrame, ByteString, Any],
    idleTimeout: FiniteDuration                                       = defaultIdleTimeout
  ) =
    containerAttachAsSource(id, streams, idleTimeout)
      .join(stdStreamFrameCodec.join(flow))
      .run()

  def containerAttachAsTtyStream(
    id:          String,
    streams:     Set[StdStream.StdStream],
    flow:        Flow[ByteString, ByteString, Any],
    idleTimeout: FiniteDuration                    = defaultIdleTimeout
  ) =
    containerAttachAsSource(id, streams, idleTimeout)
      .join(flow)
      .run()

  def containerWait(id: String) =
    apiJsonRequest(HttpMethods.POST, s"/containers/$id/wait")
      .map(_.convertTo[ContainerWaitResponse])

  def containerRemove(
    id:          String,
    withForce:   Boolean = false,
    withVolumes: Boolean = false
  ) = {
    val params = Map(
      "force" → withForce.toString,
      "v" → withVolumes.toString
    )
    apiRequestAsSource(HttpMethods.DELETE, s"/containers/$id", params)
      .map(_ ⇒ ())
  }

  def containerCopy(id: String, path: String) = {
    val entity = requestJsonEntity(CopyConfig(path))
    apiRequestAsSource(HttpMethods.POST, s"/containers/$id/copy", entity = entity)
      .map(_._1)
  }

  private def parseContainerPathStat(headers: Seq[HttpHeader]) = {
    headers.find(_.is("x-docker-container-path-stat")).map { header ⇒
      val json = new String(Base64.decodeBase64(header.value))
      json.parseJson.convertTo[ContainerPathStat]
    }
  }

  def containerArchiveInformation(id: String, path: String) =
    apiRequestAsSource(HttpMethods.HEAD, s"/containers/$id/archive", Map("path" → path))
      .map(res ⇒ parseContainerPathStat(res._2))

  def containerArchive(id: String, path: String) =
    apiRequestAsSource(HttpMethods.GET, s"/containers/$id/archive", Map("path" → path)).map { res ⇒
      (res._1, parseContainerPathStat(res._2))
    }

  def containerArchiveExtract(
    id:            String,
    source:        Source[ByteString, Any],
    path:          String,
    withOverwrite: Boolean                 = false
  ) = {
    val params = Map(
      "path" → path,
      "noOverwriteDirNonDir" → (!withOverwrite).toString
    )
    val entity = requestTarEntity(source)
    apiRequestAsSource(HttpMethods.PUT, s"/containers/$id/archive", params, entity)
      .map(_ ⇒ ())
  }

  def images(
    showAll:  Boolean                  = false,
    filters:  Map[String, Set[String]] = Map.empty,
    withName: Option[String]           = None
  ) = {
    val params = Map(
      "all" → showAll.toString,
      "filters" → filters.toJson.compactPrint,
      "filter" → withName.toParam()
    )
    apiJsonRequest(HttpMethods.GET, "/images/json", params)
      .map(_.convertTo[List[ImageItem]])
  }

  def imageBuild(
    source:          Source[ByteString, Any],
    dockerfile:      String                  = "Dockerfile",
    name:            Option[String]          = None,
    remoteUri:       Option[String]          = None,
    showBuildOutput: Boolean                 = true,
    withCache:       Boolean                 = false,
    withPull:        Boolean                 = true,
    removeMode:      RemoveMode.RemoveMode   = RemoveMode.Default,
    memoryLimit:     Option[Long]            = None,
    memorySwapLimit: Option[Long]            = None,
    cpuShares:       Option[Int]             = None,
    cpusetCpus:      Option[String]          = None,
    cpuPeriod:       Option[Long]            = None,
    cpuQuota:        Option[Long]            = None,
    registryConfig:  Option[RegistryConfig]  = None
  ) = {
    val params = Map(
      "dockerfile" → dockerfile,
      "t" → name.toParam(),
      "remote" → remoteUri.toParam(),
      "q" → (!showBuildOutput).toString,
      "nocache" → (!withCache).toString,
      "pull" → withPull.toString,
      "memory" → memoryLimit.toParam(),
      "memswap" → memorySwapLimit.toMemorySwapParam(),
      "cpushares" → cpuShares.toParam(),
      "cpusetcpus" → cpusetCpus.toParam(),
      "cpuperiod" → cpuPeriod.toParam(),
      "cpuquota" → cpuQuota.toParam()
    ) ++ RemoveMode.mapToParams(removeMode)
    val entity = requestTarEntity(source)
    val headers = RegistryConfig.mapToHeaders(registryConfig)
    apiJsonRequestAsSource(HttpMethods.POST, s"/build", params, entity, headers = headers)
      .map(_.map(_.convertTo[EventMessage]))
  }

  def imagePull(
    name:           String,
    repositoryName: Option[String]     = None,
    tag:            Option[String]     = None,
    registry:       Option[Registry]   = None,
    registryAuth:   Option[AuthConfig] = None,
    idleTimeout:    FiniteDuration     = defaultIdleTimeout
  ) = {
    val params = Map(
      "fromImage" → name,
      "tag" → tag.toParam(),
      "repo" → repositoryName.toParam(),
      "registry" → registry.toParam()
    )
    val headers = AuthConfig.mapToHeaders(registryAuth)
    apiJsonRequestAsSource(
      HttpMethods.POST,
      s"/images/create",
      params,
      headers = headers,
      idleTimeout = Some(idleTimeout)
    ).map(_.map(_.convertTo[EventMessage]))
  }

  def imagePullWithUrl(
    url:            URL,
    repositoryName: Option[String]     = None,
    tag:            Option[String]     = None,
    registry:       Option[Registry]   = None,
    registryAuth:   Option[AuthConfig] = None,
    idleTimeout:    FiniteDuration     = defaultIdleTimeout
  ) = {
    val params = Map(
      "fromSrc" → url.toString,
      "tag" → tag.toParam(),
      "repo" → repositoryName.toParam(),
      "registry" → registry.toParam()
    )
    val headers = AuthConfig.mapToHeaders(registryAuth)
    apiJsonRequestAsSource(
      HttpMethods.POST,
      s"/images/create",
      params,
      headers = headers,
      idleTimeout = Some(idleTimeout)
    ).map(_.map(_.convertTo[EventMessage]))
  }

  def imagePullWithStream(
    source:         Source[ByteString, Any],
    repositoryName: Option[String]          = None,
    tag:            Option[String]          = None,
    registry:       Option[Registry]        = None,
    registryAuth:   Option[AuthConfig]      = None,
    idleTimeout:    FiniteDuration          = defaultIdleTimeout
  ) = {
    val params = Map(
      "fromSrc" → "-",
      "tag" → tag.toParam(),
      "repo" → repositoryName.toParam(),
      "registry" → registry.toParam()
    )
    val entity = requestTarEntity(source)
    val headers = AuthConfig.mapToHeaders(registryAuth)
    apiJsonRequestAsSource(
      HttpMethods.POST,
      "/images/create",
      params,
      entity,
      headers = headers,
      idleTimeout = Some(idleTimeout)
    ).map(_.map(_.convertTo[EventMessage]))
  }

  def imageInspect(id: String) =
    apiJsonRequest(HttpMethods.GET, s"/images/$id/json")
      .map(_.convertTo[ImageInspect])

  def imageHistory(id: String) =
    apiJsonRequest(HttpMethods.GET, s"/images/$id/history")
      .map(_.convertTo[List[ImageHistory]])

  def imagePush(
    id:           String,
    tag:          Option[String]     = None,
    registry:     Option[Registry]   = None,
    registryAuth: Option[AuthConfig] = None
  ) = {
    val params = Map("tag" → tag.toParam())
    val headers = AuthConfig.mapToHeaders(registryAuth)
    val name = registry match {
      case Some(r) ⇒ s"${r.toParam}/$id"
      case None    ⇒ id
    }
    apiJsonRequestAsSource(HttpMethods.POST, s"/images/$name/push", params, headers = headers)
      .map(_.map(_.convertTo[EventMessage]))
  }

  def imageTag(
    id:             String,
    repositoryName: String,
    tag:            Option[String] = None,
    withForce:      Boolean        = false
  ) = {
    val params = Map(
      "tag" → tag.toParam(),
      "repo" → repositoryName,
      "force" → withForce.toString
    )
    apiRequestAsSource(HttpMethods.POST, s"/images/$id/tag", params)
      .map(_ ⇒ ())
  }

  def imageRemove(
    id:        String,
    withPrune: Boolean = true,
    withForce: Boolean = false
  ) = {
    val params = Map(
      "noprune" → (!withPrune).toString(),
      "force" → withForce.toString()
    )
    apiRequestAsSource(HttpMethods.DELETE, s"/images/$id", params)
      .map(_ ⇒ ())
  }

  def imageSearch(term: String) =
    apiJsonRequest(HttpMethods.GET, "/images/search", Map("term" → term))
      .map(_.convertTo[List[ImageSearchResult]])

  def imageCommit(
    containerId:    String,
    repositoryName: Option[String]    = None,
    tag:            Option[String]    = None,
    comment:        Option[String]    = None,
    author:         Option[String]    = None,
    withPause:      Boolean           = false,
    changes:        List[String]      = List.empty,
    config:         Option[RunConfig] = None
  ) = {
    val params = Map(
      "container" → containerId,
      "repo" → repositoryName.toParam(),
      "tag" → tag.toParam(),
      "comment" → comment.toParam(),
      "author" → author.toParam(),
      "pause" → withPause.toString,
      "changes" → changes.mkString("\n")
    )
    val entity = config match {
      case Some(c) ⇒ requestJsonEntity(c)
      case None    ⇒ HttpEntity.Empty
    }
    apiJsonRequest(HttpMethods.POST, "/commit", params, entity)
      .map(_.convertTo[ContainerCommitResponse])
  }

  def imageGet(
    id:  String,
    tag: Option[String] = None
  ) = {
    val name = tag match {
      case Some(t) ⇒ s"$id:$t"
      case None    ⇒ id
    }
    apiRequestAsSource(HttpMethods.GET, s"/images/$name/get")
      .map(_._1)
  }

  def imagesGet(names: List[String]) = {
    val q = names.foldRight(Seq.empty[(String, String)]) {
      case (name, acc) ⇒ ("names", name) +: acc
    }
    val uri = Uri("/images/get").withQuery(Uri.Query(q: _*))
    apiRequestAsSource(HttpMethods.GET, uri)
      .map(_._1)
  }

  def imagesLoad(source: Source[ByteString, Any]) = {
    val entity = requestTarEntity(source)
    apiRequestAsSource(HttpMethods.POST, "/images/load", entity = entity)
      .map(_ ⇒ ())
  }

  def auth(config: AuthConfig) =
    apiRequestAsSource(HttpMethods.POST, "/auth", entity = requestJsonEntity(config))
      .map(_ ⇒ ())

  def ping() =
    apiRequestAsSource(HttpMethods.GET, "/_ping")
      .map(_ ⇒ ())

  def eventsAsStream(
    since:       Option[ZonedDateTime] = None,
    until:       Option[ZonedDateTime] = None,
    filters:     EventsFilter          = Map.empty,
    idleTimeout: FiniteDuration        = defaultIdleTimeout
  ) = {
    val params = Map(
      "since" → since.toParamAsTimestamp(),
      "until" → since.toParamAsTimestamp(),
      "filters" → EventsFitler.mapToParam(filters)
    )
    apiJsonRequestAsSource(HttpMethods.GET, "/events", params, idleTimeout = Some(idleTimeout))
      .map(_.map(_.convertTo[EventKindMessage]))
  }

  def execCreate(
    containerId:  String,
    command:      List[String],
    streams:      Set[StdStream.StdStream],
    isTty:        Boolean,
    user:         Option[String]           = None,
    isPrivileged: Boolean                  = false
  ) = {
    val config = ExecConfig(
      command = command,
      user = user,
      isPrivileged = isPrivileged,
      isTty = isTty,
      containerId = Some(containerId),
      isAttachStdin = streams.contains(StdStream.In),
      isAttachStdout = streams.contains(StdStream.Out),
      isAttachStderr = streams.contains(StdStream.Err),
      isDetach = false
    )
    val entity = requestJsonEntity(config)
    apiJsonRequest(HttpMethods.POST, s"/containers/$containerId/exec", entity = entity)
      .map(_.convertTo[ContainerExecCreateResponse])
  }

  def execStart(
    id:    String,
    isTty: Boolean
  ) = {
    val entity = requestJsonEntity(ExecStartCheck(
      isDetach = true,
      isTty = isTty
    ))
    apiRequestAsSource(HttpMethods.POST, s"/exec/$id/start", entity = entity)
      .map(_ ⇒ ())
  }

  private def execStartAsSource(
    id:          String,
    isTty:       Boolean,
    idleTimeout: FiniteDuration
  ) = {
    val entity = requestJsonEntity(ExecStartCheck(
      isDetach = false,
      isTty = isTty
    ))
    val req = HttpRequest(
      method = HttpMethods.POST,
      uri = s"/exec/$id/start",
      entity = entity,
      headers = upgradeHeaders
    )
    hijackConnectionFlow(req, idleTimeout)
  }

  def execAttachAsStream(
    id:          String,
    flow:        Flow[StdStreamFrame.StdStreamFrame, ByteString, Any],
    idleTimeout: FiniteDuration                                       = defaultIdleTimeout
  ) =
    execStartAsSource(id, false, idleTimeout)
      .join(stdStreamFrameCodec.join(flow))
      .run()

  def execAttachAsTtyStream(
    id:          String,
    flow:        Flow[ByteString, ByteString, Any],
    idleTimeout: FiniteDuration                    = defaultIdleTimeout
  ) =
    execStartAsSource(id, true, idleTimeout)
      .join(flow)
      .run()

  def execResizeTty(id: String, width: Int, height: Int) = {
    val params = Map(
      "w" → width.toString,
      "h" → height.toString
    )
    apiRequestAsSource(HttpMethods.POST, s"/exec/$id/resize", params)
      .map(_ ⇒ ())
  }

  def execInspect(id: String) =
    apiJsonRequest(HttpMethods.POST, s"/exec/$id/json")
      .map(_.convertTo[ExecConfig])
}
