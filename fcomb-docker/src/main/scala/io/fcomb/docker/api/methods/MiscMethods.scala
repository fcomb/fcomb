package io.fcomb.docker.api.methods

import akka.http.scaladsl.model.headers.RawHeader
import scala.collection.immutable
import spray.json._
import org.apache.commons.codec.binary.Base64
import java.time.ZonedDateTime

object MiscMethods {
  final case class IndexInfo(
    name: String,
    mirrors: List[String],
    isSecure: Boolean,
    isOfficial: Boolean
  )

  final case class ServiceConfig(
    insecureRegistryCidrs: List[String],
    indexConfigs: Map[String, IndexInfo]
  )

  final case class Information(
    id: String,
    continers: Int,
    images: Int,
    driver: String,
    driverStatus: List[List[String]],
    isMemoryLimit: Boolean,
    isSwapLimit: Boolean,
    isCpuCfsPeriod: Boolean,
    isCpuCfsQuota: Boolean,
    isIpv4Forwarding: Boolean,
    isBridgeNfIptables: Boolean,
    isBridgeNfIp6tables: Boolean,
    isDebug: Boolean,
    fileDescriptors: Int,
    isOomKillDisable: Boolean,
    goroutines: Int,
    systemTime: ZonedDateTime,
    executionDriver: String,
    loggingDriver: Option[String],
    eventsListeners: Int,
    kernelVersion: String,
    operatingSystem: String,
    indexServerAddress: String,
    registryConfig: Option[ServiceConfig],
    initSha1: String,
    initPath: String,
    cpus: Int,
    memory: Long,
    dockerRootDir: String,
    httpProxy: Option[String],
    httpsProxy: Option[String],
    noProxy: Option[String],
    name: Option[String],
    labels: Map[String, String],
    isExperimentalBuild: Boolean
  ) extends DockerApiResponse

  final case class ExecStartCheck(
    isDetach: Boolean,
    isTty: Boolean
  )

  final case class Version(
    version: String,
    apiVersion: String,
    gitCommit: String,
    goVersion: String,
    os: String,
    arch: String,
    kernelVersion: Option[String],
    experimental: Boolean,
    buildTime: Option[String]
  )

  final case class AuthConfig(
    username: String,
    password: String,
    email: Option[String],
    serverAddress: String
  ) extends DockerApiRequest

  object AuthConfig {
    import io.fcomb.docker.api.json.MiscMethodsFormat.authConfigFormat

    private val emptyConfig = Base64.encodeBase64String(JsObject().compactPrint.getBytes)

    def mapToHeaders(configOpt: Option[AuthConfig]) = {
      val value = configOpt match {
        case Some(config) => mapToJsonAsBase64(config)
        case None => emptyConfig
      }
      immutable.Seq(RawHeader("X-Registry-Auth", value))
    }
  }

  abstract class DockerEvent(val value: String)

  abstract class ContainerEvent(value: String) extends DockerEvent(value)

  abstract class ImageEvent(value: String) extends DockerEvent(value)

  object DockerEvent {
    val all: Set[DockerEvent] = ContainerEvent.all ++ ImageEvent.all
  }

  object ContainerEvent {
    case object Attach extends ContainerEvent("attach")
    case object Commit extends ContainerEvent("commit")
    case object Copy extends ContainerEvent("copy")
    case object Create extends ContainerEvent("create")
    case object Destroy extends ContainerEvent("destroy")
    case object Die extends ContainerEvent("die")
    case object ExecCreate extends ContainerEvent("exec_create")
    case object ExecStart extends ContainerEvent("exec_start")
    case object Export extends ContainerEvent("export")
    case object Kill extends ContainerEvent("kill")
    case object Oom extends ContainerEvent("oom")
    case object Pause extends ContainerEvent("pause")
    case object Rename extends ContainerEvent("rename")
    case object Resize extends ContainerEvent("resize")
    case object Restart extends ContainerEvent("restart")
    case object Start extends ContainerEvent("start")
    case object Stop extends ContainerEvent("stop")
    case object Top extends ContainerEvent("top")
    case object Unpause extends ContainerEvent("unpause")

    val all: Set[DockerEvent] = Set(Attach, Commit, Copy, Create, Destroy, Die,
      ExecCreate, ExecStart, Export, Kill, Oom, Pause, Rename,
      Resize, Restart, Start, Stop, Top, Unpause)
  }

  object ImageEvent {
    case object Delete extends ImageEvent("delete")
    case object Import extends ImageEvent("import")
    case object Pull extends ImageEvent("pull")
    case object Push extends ImageEvent("push")
    case object Tag extends ImageEvent("tag")
    case object Untag extends ImageEvent("untag")

    val all: Set[DockerEvent] = Set(Delete, Import, Pull, Push, Tag, Untag)
  }

  object EventKind extends Enumeration {
    type EventKind = Value

    val Event = Value("event")
    val Image = Value("iamge")
    val Container = Value("container")
  }

  type EventsFilter = Map[EventKind.EventKind, Set[DockerEvent]]

  object EventsFitler {
    def mapToParam(f: EventsFilter) = JsObject(f.map {
      case (k, events) =>
        k.toString -> JsArray(events.map(e => JsString(e.value)).toSeq: _*)
    }).compactPrint
  }
}
