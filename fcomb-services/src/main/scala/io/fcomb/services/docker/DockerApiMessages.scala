package io.fcomb.services.docker

import spray.json._
import spray.json.DefaultJsonProtocol.{listFormat => _, _}
import io.fcomb.json._
import java.time.LocalDateTime

object DockerApiMessages {
  sealed trait DockerApiMessage

  sealed trait DockerApiResponse extends DockerApiMessage

  sealed trait DockerApiRequest extends DockerApiMessage

  case class IndexInfo(
    name: String,
    mirrors: List[String],
    isSecure: Boolean,
    isOfficial: Boolean
  )

  case class ServiceConfig(
    insecureRegistryCidrs: List[String],
    indexConfigs: Map[String, IndexInfo]
  )

  case class Info(
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
    systemTime: String,
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
    labels: List[String],
    isExperimentalBuild: Boolean
  ) extends DockerApiResponse

  object PortKind extends Enumeration {
    type PortKind = Value

    val Tcp = Value("tcp")
    val Udp = Value("udp")
  }

  case class Port(
    privatePort: Int,
    publicPort: Option[Int],
    kind: PortKind.PortKind,
    ip: Option[String]
  )

  case class ContainerItem(
    id: String,
    names: List[String],
    image: String,
    command: String,
    created: Long,
    status: String,
    ports: List[Port],
    sizeRw: Option[Long],
    sizeRootFs: Option[Long]
  ) extends DockerApiResponse

  case class MountPoint(
    source: String,
    destination: String,
    mode: String, // TODO
    isReadAndWrite: Boolean
  )

  case class PortBinding(
    port: Int,
    ip: Option[String]
  )

  case class RestartPolicy(
    name: Option[String],
    maximumRetryCount: Int
  )

  case class HostConfigNetworkMode(
    networkMode: String
  )

  case class Container(
    id: String,
    names: List[String],
    image: String,
    command: String,
    created: Int,
    ports: List[Port],
    sizeRw: Option[Int],
    sizeRootFs: Option[Int],
    labels: Map[String, String],
    status: String,
    hostConfig: HostConfigNetworkMode
  )

  case class CopyConfig(
    resource: String
  )

  case class ContainerProcessList(
    processes: List[List[String]],
    titles: List[String]
  )

  case class Version(
    version: String,
    apiVersion: String,
    gitCommit: String,
    goVersion: String,
    os: String,
    arch: String,
    kernelVersion: Option[String],
    experimental: Option[Boolean],
    buildTime: Option[String]
  )

  case class HostConfig(
    binds: List[String],
    links: List[String],
    lxcConf: Map[String, String],
    memory: Long,
    memorySwap: Long,
    cpuShares: Int,
    cpuPeriod: Long,
    cpusetCpus: String,
    cpusetMems: String,
    blockIoWeight: Int,
    memorySwappiness: Int,
    isOomKillDisable: Boolean,
    portBindings: Map[String, List[PortBinding]],
    isPublishAllPorts: Boolean,
    isPrivileged: Boolean,
    isReadonlyRootfs: Boolean,
    dns: List[String],
    dnsSearch: List[String],
    extraHosts: Option[String],
    volumesFrom: List[String],
    capacityAdd: List[String],
    capacityDrop: List[String],
    restartPolicy: RestartPolicy,
    networkMode: String, // TODO: make it as enum
    devices: List[String],
    // Ulimits,
    // LogConfig,
    // SecurityOpt,
    cgroupParent: Option[String]
  )

  case class ContainerCreate(
    hostname: Option[String],
    domainName: Option[String],
    user: Option[String],
    isAttachStdin: Boolean,
    isAttachStdout: Boolean,
    isAttachStderr: Boolean,
    isTty: Boolean,
    isOpenStdin: Boolean,
    isStdinOnce: Boolean,
    env: List[String],
    command: List[String],
    entrypoint: Option[String],
    image: String,
    labels: Map[String, String],
    mounts: List[MountPoint],
    isNetworkDisabled: Boolean,
    workingDirectory: Option[String],
    macAddress: Option[String],
    // exposedPorts: Map[String],
    hostConfig: HostConfig
  ) extends DockerApiRequest

  case class ContainerCreateResponse(
    id: String,
    warnings: List[String]
  ) extends DockerApiResponse

  case class ContainerExecCreateResponse(
    id: String
  ) extends DockerApiResponse

  case class AuthResponse(
    status: String
  ) extends DockerApiResponse

  case class ContainerWaitResponse(
    statusCode: Int
  ) extends DockerApiResponse

  case class ContainerCommitResponse(
    id: String
  ) extends DockerApiResponse

  case class ContainerChange(
    kind: Int,
    path: String
  ) extends DockerApiResponse

  case class ImageHistory(
    id: String,
    created: Long,
    createdBy: String,
    tags: List[String],
    size: Long,
    comment: String
  )

  case class ImageDelete(
    untagged: String,
    deleted: String
  )

  case class Image(
    id: String,
    parentId: String,
    repositoryTags: List[String],
    repositoryDigests: List[String],
    created: Int,
    size: Int,
    virtualSize: Int,
    labels: Map[String, String]
  )

  case class GraphDriverData(
    name: String,
    data: Map[String, String]
  )

  case class ImageInspect(
    id: String,
    parent: String,
    comment: String,
    created: LocalDateTime,
    container: String,
    // TODO: containerConfig: *runconfig.Config
    dockerVersion: String,
    author: String,
    // config          *runconfig.Config
    architecture: String,
    os: String,
    size: Long,
    virtualSize: Long,
    graphDriver: GraphDriverData
  )

  object JsonProtocols {
    private def optString(v: Option[String]) = v match {
      case res @ Some(s) if s.nonEmpty => res
      case _ => None
    }

    private implicit def listFormat[T: JsonFormat] = new RootJsonFormat[List[T]] {
      def write(list: List[T]) = list match {
        case Nil => JsNull
        case xs => JsArray(xs.map(_.toJson).toVector)
      }

      def read(value: JsValue): List[T] = value match {
        case JsArray(xs) => xs.map(_.convertTo[T])(collection.breakOut)
        case JsNull => List.empty
        case x => deserializationError(s"Expected List as JsArray, but got $x")
      }
    }

    implicit val indexInfoFormat =
      jsonFormat(IndexInfo, "Name", "Mirrors", "Secure", "Official")

    implicit val serviceConfigFormat =
      jsonFormat(ServiceConfig, "InsecureRegistryCIDRs", "IndexConfigs")

    implicit object InfoFormat extends RootJsonReader[Info] {
      def read(v: JsValue) = v match {
        case obj: JsObject => Info(
          id = obj.get[String]("ID"),
          continers = obj.get[Int]("Containers"),
          images = obj.get[Int]("Images"),
          driver = obj.get[String]("Driver"),
          driverStatus = obj.get[List[List[String]]]("DriverStatus"),
          isMemoryLimit = obj.get[Boolean]("MemoryLimit"),
          isSwapLimit = obj.get[Boolean]("SwapLimit"),
          isCpuCfsPeriod = obj.getOrElse[Boolean]("CpuCfsPeriod", false),
          isCpuCfsQuota = obj.getOrElse[Boolean]("CpuCfsQuota", false),
          isIpv4Forwarding = obj.get[Boolean]("IPv4Forwarding"),
          isBridgeNfIptables = obj.getOrElse[Boolean]("BridgeNfIptables", false),
          isBridgeNfIp6tables = obj.getOrElse[Boolean]("BridgeNfIp6tables", false),
          isDebug = obj.get[Boolean]("Debug"),
          fileDescriptors = obj.get[Int]("NFd"),
          isOomKillDisable = obj.getOrElse[Boolean]("OomKillDisable", true),
          goroutines = obj.get[Int]("NGoroutines"),
          systemTime = obj.get[String]("SystemTime"),
          executionDriver = obj.get[String]("ExecutionDriver"),
          loggingDriver = obj.getOpt[String]("LoggingDriver"),
          eventsListeners = obj.get[Int]("NEventsListener"),
          kernelVersion = obj.get[String]("KernelVersion"),
          operatingSystem = obj.get[String]("OperatingSystem"),
          registryConfig = obj.getOpt[ServiceConfig]("RegistryConfig"),
          indexServerAddress = obj.get[String]("IndexServerAddress"),
          initSha1 = obj.get[String]("InitSha1"),
          initPath = obj.get[String]("InitPath"),
          cpus = obj.get[Int]("NCPU"),
          memory = obj.get[Long]("MemTotal"),
          dockerRootDir = obj.get[String]("DockerRootDir"),
          httpProxy = optString(obj.getOpt[String]("HttpProxy")),
          httpsProxy = optString(obj.getOpt[String]("HttpsProxy")),
          noProxy = optString(obj.getOpt[String]("NoProxy")),
          name = optString(obj.getOpt[String]("Name")),
          labels = obj.get[List[String]]("Labels"),
          isExperimentalBuild = obj.getOrElse[Boolean]("ExperimentalBuild", false)
        )
        case x => deserializationError(s"Expected JsObject, but got $x")
      }
    }

    implicit val portKindFormat =
      createEnumerationJsonFormat(PortKind)

    implicit val portFormat =
      jsonFormat(Port, "PrivatePort", "PublicPort", "Type", "IP")

    implicit val containerItemFormat =
      jsonFormat(ContainerItem, "Id", "Names", "Image", "Command", "Created",
        "Status", "Ports", "SizeRw", "SizeRootFs")

    implicit val mountPointFormat =
      jsonFormat(MountPoint, "Source", "Destination", "Mode", "RW")

    implicit val portBindingFormat =
      jsonFormat(PortBinding, "HostPort", "HostIp")

    implicit val restartPolicyFormat =
      jsonFormat(RestartPolicy, "Name", "MaximumRetryCount")

    implicit object HostConfigFormat extends RootJsonFormat[HostConfig] {
      def write(c: HostConfig) = JsObject(
        "Binds" -> c.binds.toJson,
        "Links" -> c.links.toJson,
        "LxcConf" -> c.lxcConf.toJson,
        "Memory" -> c.memory.toJson,
        "MemorySwap" -> c.memorySwap.toJson,
        "CpuShares" -> c.cpuShares.toJson,
        "CpuPeriod" -> c.cpuPeriod.toJson,
        "CpusetCpus" -> c.cpusetCpus.toJson,
        "CpusetMems" -> c.cpusetMems.toJson,
        "blckWeight" -> c.blockIoWeight.toJson,
        "MemorySwappiness" -> c.memorySwappiness.toJson,
        "OomKillDisable" -> c.isOomKillDisable.toJson,
        "PortBindings" -> c.portBindings.toJson,
        "PublishAllPorts" -> c.isPublishAllPorts.toJson,
        "Privileged" -> c.isPrivileged.toJson,
        "ReadonlyRootfs" -> c.isReadonlyRootfs.toJson,
        "Dns" -> c.dns.toJson,
        "DnsSearch" -> c.dnsSearch.toJson,
        "ExtraHosts" -> c.extraHosts.toJson,
        "VolumesFrom" -> c.volumesFrom.toJson,
        "CapAdd" -> c.capacityAdd.toJson,
        "CapDrop" -> c.capacityDrop.toJson,
        "RestartPolicy" -> c.restartPolicy.toJson,
        "NetworkMode" -> c.networkMode.toJson,
        "Devices" -> c.devices.toJson,
        "CgroupParent" -> c.cgroupParent.toJson
      )

      def read(v: JsValue) = deserializationError("Not implemented")
    }

    implicit val containerCreateFormat =
      jsonFormat(ContainerCreate, "Hostname", "Domainname", "User", "AttachStdin",
        "AttachStdout", "AttachStderr", "Tty", "OpenStdin", "StdinOnce", "Env", "Cmd",
        "Entrypoint", "Image", "Labels", "Mounts", "NetworkDisabled", "WorkingDir",
        "MacAddress", /*"ExposedPorts",*/ "HostConfig")

    implicit val containerCreateResponseFormat =
      jsonFormat(ContainerCreateResponse, "Id", "Warnings")

    implicit val versionFormat =
      jsonFormat(Version, "Version", "ApiVersion", "GitCommit", "GoVersion", "Os",
        "Arch", "KernelVersion", "Experimental", "BuildTime")
  }
}
