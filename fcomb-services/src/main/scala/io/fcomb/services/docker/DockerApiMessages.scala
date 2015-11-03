package io.fcomb.services.docker

import spray.json._
import spray.json.DefaultJsonProtocol.{listFormat => _, _}
import io.fcomb.json._
import java.time.{LocalDateTime, ZonedDateTime}

object DockerApiMessages {
  sealed trait DockerApiMessage

  sealed trait DockerApiResponse extends DockerApiMessage

  sealed trait DockerApiRequest extends DockerApiMessage

  sealed trait MapToString {
    def mapToString(): String
  }

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

  case class Information(
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
    labels: List[String],
    isExperimentalBuild: Boolean
  ) extends DockerApiResponse

  case class ExecStartCheck(
    isDetach: Boolean,
    isTty: Boolean
  )

  case class ContainerState(
    isRunning: Boolean,
    isPaused: Boolean,
    isRestarting: Boolean,
    isOomKilled: Boolean,
    isDead: Boolean,
    pid: Int,
    exitCode: Int,
    error: Option[String],
    startedAt: Option[ZonedDateTime],
    finishedAt: Option[ZonedDateTime]
  )

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
    createdAt: ZonedDateTime,
    status: String,
    ports: List[Port],
    sizeRw: Option[Long],
    sizeRootFs: Option[Long]
  ) extends DockerApiResponse

  object MountMode extends Enumeration {
    type MountMode = Value

    val ro = Value("ro")
    val rw = Value("rw")
    val z = Value("z")
    val Z = Value("Z")
  }

  case class MountPoint(
    source: String,
    destination: String,
    mode: Set[MountMode.MountMode],
    isReadAndWrite: Boolean
  )

  case class PortBinding(
    port: Int,
    ip: Option[String]
  )

  sealed trait RestartPolicy {
    val maximumRetryCount: Int
    val name: String
  }

  object RestartPolicy {
    case object No extends RestartPolicy {
      val maximumRetryCount = 0
      val name = "no"
    }

    case object Always extends RestartPolicy {
      val maximumRetryCount = 0
      val name = "always"
    }

    case class OnFailure(maximumRetryCount: Int) extends RestartPolicy {
      val name = OnFailure.name
    }

    private[docker] case object OnFailure {
      val name = "on-failure"
    }
  }

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

  sealed trait VolumeBindPath extends MapToString

  object VolumeBindPath {
    case class VolumePath(path: String) extends VolumeBindPath {
      def mapToString() = path
    }

    case class VolumeHostPath(
      hostPath: String,
      path: String,
      mode: MountMode.MountMode
    ) extends VolumeBindPath {
      def mapToString() = s"$hostPath:$path:$mode"
    }

    def parse(s: String) = s.split(':').toList match {
      case path :: Nil => VolumePath(path)
      case hostPath :: path :: xs =>
        val mode = xs.headOption match {
          case Some("ro") => MountMode.ro
          case _ => MountMode.rw
        }
        VolumeHostPath(hostPath, path, mode)
      case _ => deserializationError(s"Unknown bind format: $s")
    }
  }

  case class ContainerLink(
    name: String,
    alias: String
  ) extends MapToString {
    def mapToString() = s"$name:$alias"
  }

  object ContainerLink {
    def parse(s: String) = s.split(':').toList match {
      case name :: alias :: Nil => ContainerLink(name, alias)
      case _ => deserializationError(s"Unknown link format: $s")
    }
  }

  case class VolumeFrom(
    name: String,
    mode: MountMode.MountMode
  ) extends MapToString {
    def mapToString() = s"$name:$mode"
  }

  object VolumeFrom {
    def parse(s: String) = s.split(':').toList match {
      case name :: xs =>
        val mode = xs.headOption match {
          case Some("ro") => MountMode.ro
          case _ => MountMode.rw
        }
        VolumeFrom(name, mode)
      case _ => deserializationError(s"Unknown volume format: $s")
    }
  }

  case class ExtraHost(
    hostname: String,
    ip: String
  ) extends MapToString {
    def mapToString() = s"$hostname:$ip"
  }

  object ExtraHost {
    def parse(s: String) = s.split(':').toList match {
      case hostname :: ip :: Nil => ExtraHost(hostname, ip)
      case _ => deserializationError(s"Unknown host format: $s")
    }
  }

  object Capacity extends Enumeration {
    type Capacity = Value

    val SetPcap = "SETPCAP"
    val SysModule = "SYS_MODULE"
    val SysRawIo = "SYS_RAWIO"
    val SysPacct = "SYS_PACCT"
    val SysAdmib = "SYS_ADMIN"
    val SysNice = "SYS_NICE"
    val SysResource = "SYS_RESOURCE"
    val SysTime = "SYS_TIME"
    val SysTtyConfig = "SYS_TTY_CONFIG"
    val Mknod = "MKNOD"
    val AuditWrite = "AUDIT_WRITE"
    val AuditControl = "AUDIT_CONTROL"
    val MacOverride = "MAC_OVERRIDE"
    val MacAdmin = "MAC_ADMIN"
    val NetAdmin = "NET_ADMIN"
    val Syslog = "SYSLOG"
    val Chown = "CHOWN"
    val NetRaw = "NET_RAW"
    val DacOverride = "DAC_OVERRIDE"
    val Fowner = "FOWNER"
    val DacReadSearch = "DAC_READ_SEARCH"
    val Fsetid = "FSETID"
    val Kill = "KILL"
    val Setgid = "SETGID"
    val Setuid = "SETUID"
    val LinuxImmutable = "LINUX_IMMUTABLE"
    val NetBindService = "NET_BIND_SERVICE"
    val NetBroadcast = "NET_BROADCAST"
    val IpcLock = "IPC_LOCK"
    val IpcOwner = "IPC_OWNER"
    val SysChroot = "SYS_CHROOT"
    val SysPtrace = "SYS_PTRACE"
    val SysBoot = "SYS_BOOT"
    val Lease = "LEASE"
    val Setfcap = "SETFCAP"
    val WakeAlarm = "WAKE_ALARM"
    val BlockSuspend = "BLOCK_SUSPEND"
    val AuditRead = "AUDIT_READ"
  }

  sealed trait NetworkMode extends MapToString

  object NetworkMode {
    case object Bridge extends NetworkMode {
      def mapToString() = "bridge"
    }

    case object Host extends NetworkMode {
      def mapToString() = "host"
    }

    case class ContainerHost(name: String) extends NetworkMode {
      def mapToString() = s"container:$name"
    }

    case object None extends NetworkMode {
      def mapToString() = "none"
    }

    case object Default extends NetworkMode {
      def mapToString() = "default"
    }

    def parse(s: String) = s.split(':').toList match {
      case "bridge" :: Nil => Bridge
      case "host" :: Nil => Host
      case "container" :: name :: Nil => ContainerHost(name)
      case "none" :: Nil => None
      case "default" :: Nil => Default
      case m => deserializationError(s"Unknown network mode: $m")
    }
  }

  sealed trait IpcMode extends MapToString

  object IpcMode {
    case object Host extends IpcMode {
      def mapToString() = "host"
    }

    case class ContainerHost(name: String) extends IpcMode {
      def mapToString() = s"container:$name"
    }

    def parse(s: String) = s.split(':').toList match {
      case "" :: Nil | "host" :: Nil => Host
      case "container" :: name :: Nil => ContainerHost(name)
      case m => deserializationError(s"Unknown IPC mode: $m")
    }
  }

  sealed trait UtsMode extends MapToString

  object UtsMode {
    case object Host extends UtsMode {
      def mapToString() = "host"
    }

    def parse(s: String) = s.split(':').toList match {
      case "" :: Nil | "host" :: Nil => Host
      case m => deserializationError(s"Unknown UTS mode: $m")
    }
  }

  sealed trait PidMode extends MapToString

  object PidMode {
    case object Host extends PidMode {
      def mapToString() = "host"
    }

    def parse(s: String) = s.split(':').toList match {
      case "" :: Nil | "host" :: Nil => Host
      case m => deserializationError(s"Unknown PID mode: $m")
    }
  }

  case class DeviceMapping(
    pathOnHost: String,
    pathInContainer: String,
    cgroupPermissions: String
  )

  case class Ulimit(
    name: String,
    soft: Int,
    hard: Int
  )

  object LogDriver extends Enumeration {
    type LogDriver = Value

    val JsonFile = Value("json-file")
    val Syslog = Value("syslog")
    val Journald = Value("journald")
    val Gelf = Value("gelf")
    val None = Value("none")
  }

  case class LogConfig(
    kind: LogDriver.LogDriver,
    config: Map[String, String] = Map.empty
  )

  type PortBindings = Map[ExposePort, List[PortBinding]]

  case class HostConfig(
    binds: List[VolumeBindPath] = List.empty,
    links: List[ContainerLink] = List.empty,
    lxcConf: Map[String, String] = Map.empty,
    memory: Option[Long] = None,
    memorySwap: Option[Long] = None,
    cpuShares: Option[Int] = None,
    cpuPeriod: Option[Long] = None,
    cpusetCpus: Option[String] = None,
    cpusetMems: Option[String] = None,
    blockIoWeight: Option[Int] = None,
    memorySwappiness: Option[Int] = None,
    isOomKillDisable: Boolean = false,
    portBindings: PortBindings = Map.empty,
    isPublishAllPorts: Boolean = false,
    isPrivileged: Boolean = false,
    isReadonlyRootfs: Boolean = false,
    dns: List[String] = List.empty,
    dnsSearch: List[String] = List.empty,
    extraHosts: List[ExtraHost] = List.empty,
    volumesFrom: List[VolumeFrom] = List.empty,
    ipcMode: Option[IpcMode] = None,
    pidMode: Option[PidMode] = None,
    utsMode: Option[UtsMode] = None,
    capacityAdd: List[Capacity.Capacity] = List.empty,
    capacityDrop: List[Capacity.Capacity] = List.empty,
    restartPolicy: RestartPolicy = RestartPolicy.No,
    networkMode: NetworkMode = NetworkMode.Bridge,
    devices: List[DeviceMapping] = List.empty,
    ulimits: List[Ulimit] = List.empty,
    logConfig: Option[LogConfig] = None,
    securityOpt: List[String] = List.empty,
    cgroupParent: Option[String] = None
  )

  sealed trait ExposePort extends MapToString {
    val port: Int
    val kind: String

    def mapToString() = s"$port/$kind"
  }

  object ExposePort {
    case class Tcp(port: Int) extends ExposePort {
      val kind = "tcp"
    }

    case class Udp(port: Int) extends ExposePort {
      val kind = "udp"
    }

    def parse(s: String) = s.split('/').toList match {
      case p :: "tcp" :: Nil => Tcp(p.toInt)
      case p :: "udp" :: Nil => Udp(p.toInt)
      case _ => deserializationError(s"Unknown port and protocol: $s")
    }
  }

  type ExposedPorts = Set[ExposePort]

  case class ContainerCreate(
    image: String,
    hostname: Option[String] = None,
    domainName: Option[String] = None,
    user: Option[String] = None,
    isAttachStdin: Boolean = false,
    isAttachStdout: Boolean = false,
    isAttachStderr: Boolean = false,
    isTty: Boolean = false,
    isOpenStdin: Boolean = false,
    isStdinOnce: Boolean = false,
    env: List[String] = List.empty,
    command: List[String] = List.empty,
    entrypoint: Option[String] = None,
    labels: Map[String, String] = Map.empty,
    mounts: List[MountPoint] = List.empty,
    isNetworkDisabled: Boolean = false,
    workingDirectory: Option[String] = None,
    macAddress: Option[String] = None,
    exposedPorts: ExposedPorts = Set.empty,
    hostConfig: HostConfig = HostConfig()
  ) extends DockerApiRequest

  case class Address(
    address: String,
    prefixLength: Int
  )

  case class NetworkSettings(
    bridge: Option[String],
    endpointId: Option[String],
    gateway: Option[String],
    globalIpv6Address: Option[String],
    globalIpv6PrefixLength: Int,
    hairpinMode: Boolean,
    ipAddress: Option[String],
    ipPrefixLength: Int,
    ipv6Gateway: Option[String],
    linkLocalIpv6Address: Option[String],
    linkLocalIpv6PrefixLength: Int,
    macAddress: Option[String],
    networkId: Option[String],
    ports: Map[String, List[PortBinding]],
    sandboxKey: Option[String],
    secondaryIpAddresses: List[Address],
    secondaryIpv6Addresses: List[Address]
  )

  case class ContainerBase(
    id: String,
    createdAt: ZonedDateTime,
    path: String,
    args: List[String],
    state: ContainerState,
    image: String,
    networkSettings: NetworkSettings,
    resolvConfPath: Option[String],
    hostnamePath: Option[String],
    hostsPath: Option[String],
    logPath: Option[String],
    name: String,
    restartCount: Int,
    driver: String,
    execDriver: String,
    mountLabel: Option[String],
    processLabel: Option[String],
    volumes: Map[String, String],
    volumesRw: Map[String, Boolean],
    appArmorProfile: Option[String],
    execIds: List[String],
    hostConfig: HostConfig
  // GraphDriver     GraphDriverData
  ) extends DockerApiResponse

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
    private implicit object OptStringFormat extends RootJsonFormat[Option[String]] {
      def write(o: Option[String]) = o match {
        case Some(s) => JsString(s)
        case None => JsNull
      }

      def read(v: JsValue) = v match {
        case JsString(s) =>
          if (s.isEmpty) None
          else Some(s)
        case JsNull => None
        case x => deserializationError(s"Expected value as JsString, but got $x")
      }
    }

    private implicit object OptZonedDateTimeFormat extends RootJsonFormat[Option[ZonedDateTime]] {
      def write(o: Option[ZonedDateTime]) = o match {
        case Some(d) => JsString(d.toString)
        case None => JsNull
      }

      def read(v: JsValue) = v match {
        case JsString(s) =>
          val date = ZonedDateTime.parse(s)
          if (date.getYear() == 1) None
          else Some(date)
        case JsNull => None
        case x => deserializationError(s"Expected date as JsString, but got $x")
      }
    }

    private implicit def listFormat[T: JsonFormat] =
      new RootJsonFormat[List[T]] {
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

    private implicit def mapFormat[K: JsonFormat, V: JsonFormat] =
      new RootJsonFormat[Map[K, V]] {
        def write(m: Map[K, V]) = JsObject {
          m.map { field =>
            field._1.toJson match {
              case JsString(x) => x -> field._2.toJson
              case x => throw new SerializationException(s"Map key must be formatted as JsString, not '$x'")
            }
          }
        }

        def read(value: JsValue) = value match {
          case x: JsObject => x.fields.map { field =>
            (JsString(field._1).convertTo[K], field._2.convertTo[V])
          }(collection.breakOut)
          case JsNull => Map.empty
          case x => deserializationError(s"Expected Map as JsObject, but got $x")
        }
      }

    implicit val indexInformationFormat =
      jsonFormat(IndexInfo, "Name", "Mirrors", "Secure", "Official")

    implicit val serviceConfigFormat =
      jsonFormat(ServiceConfig, "InsecureRegistryCIDRs", "IndexConfigs")

    implicit object InformationFormat extends RootJsonReader[Information] {
      def read(v: JsValue) = v match {
        case obj: JsObject => Information(
          id = obj.get[String]("ID"),
          continers = obj.get[Int]("Containers"),
          images = obj.get[Int]("Images"),
          driver = obj.get[String]("Driver"),
          driverStatus = obj.getList[List[String]]("DriverStatus"),
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
          systemTime = obj.get[ZonedDateTime]("SystemTime"),
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
          httpProxy = obj.getOpt[String]("HttpProxy"),
          httpsProxy = obj.getOpt[String]("HttpsProxy"),
          noProxy = obj.getOpt[String]("NoProxy"),
          name = obj.getOpt[String]("Name"),
          labels = obj.getList[String]("Labels"),
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

    implicit object MountModeFormat extends RootJsonFormat[Set[MountMode.MountMode]] {
      def write(s: Set[MountMode.MountMode]) =
        JsString(s.mkString(","))

      def read(v: JsValue) = v match {
        case JsString(s) => s.split(',').map(MountMode.withName).toSet
        case x => deserializationError(s"Expected mode as JsString, but got $x")
      }
    }

    implicit val mountPointFormat =
      jsonFormat(MountPoint, "Source", "Destination", "Mode", "RW")

    implicit val portBindingFormat =
      jsonFormat(PortBinding, "HostPort", "HostIp")

    implicit object RestartPolicyFormat extends RootJsonFormat[RestartPolicy] {
      def write(p: RestartPolicy) = JsObject(
        "Name" -> JsString(p.name),
        "MaximumRetryCount" -> JsNumber(p.maximumRetryCount)
      )

      def read(v: JsValue) = v match {
        case obj: JsObject =>
          obj.getFields("Name", "MaximumRetryCount") match {
            case Seq(JsString(RestartPolicy.No.name), _) =>
              RestartPolicy.No
            case Seq(JsString(RestartPolicy.Always.name), _) =>
              RestartPolicy.Always
            case Seq(JsString(RestartPolicy.OnFailure.name), JsNumber(n)) =>
              RestartPolicy.OnFailure(n.toInt)
            case _ => deserializationError(s"Unknown policy: $obj")
          }
        case x => deserializationError(s"Expected policy as JsObject, but got $x")
      }
    }

    object MemorySwapFormat extends RootJsonFormat[Option[Long]] {
      def write(opt: Option[Long]) = opt match {
        case Some(n) if n >= 0 => JsNumber(n)
        case _ => JsNumber(-1)
      }

      def read(v: JsValue) = v match {
        case JsNumber(jn) => jn.toLong match {
          case -1 => None
          case n => Some(n)
        }
        case JsNull => None
        case x => deserializationError(s"Expected value as JsNumber, but got $x")
      }
    }

    object ZeroOptLongFormat extends RootJsonFormat[Option[Long]] {
      def write(opt: Option[Long]) = opt match {
        case Some(n) if n > 0L => JsNumber(n)
        case _ => JsNumber(0)
      }

      def read(v: JsValue) = v match {
        case JsNumber(jn) => jn.toLong match {
          case 0L => None
          case n => Some(n)
        }
        case JsNull => None
        case x => deserializationError(s"Expected value as JsNumber, but got $x")
      }
    }

    object ZeroOptIntFormat extends RootJsonFormat[Option[Int]] {
      def write(opt: Option[Int]) = opt match {
        case Some(n) if n > 0 => JsNumber(n)
        case _ => JsNumber(0)
      }

      def read(v: JsValue) = v match {
        case JsNumber(jn) => jn.toInt match {
          case 0 => None
          case n => Some(n)
        }
        case JsNull => None
        case x => deserializationError(s"Expected value as JsNumber, but got $x")
      }
    }

    implicit object VolumeBindPathFormat extends RootJsonFormat[VolumeBindPath] {
      def write(p: VolumeBindPath) = JsString(p.mapToString())

      def read(v: JsValue) = v match {
        case JsString(s) => VolumeBindPath.parse(s)
        case x => deserializationError(s"Expected volume as JsString, but got $x")
      }
    }

    implicit object ContainerLinkFormat extends RootJsonFormat[ContainerLink] {
      def write(l: ContainerLink) = JsString(l.mapToString())

      def read(v: JsValue) = v match {
        case JsString(s) => ContainerLink.parse(s)
        case x => deserializationError(s"Expected link as JsString, but got $x")
      }
    }

    implicit object ExtraHostFormat extends RootJsonFormat[ExtraHost] {
      def write(h: ExtraHost) = JsString(h.mapToString())

      def read(v: JsValue) = v match {
        case JsString(s) => ExtraHost.parse(s)
        case x => deserializationError(s"Expected host as JsString, but got $x")
      }
    }

    implicit object VolumeFromFormat extends RootJsonFormat[VolumeFrom] {
      def write(v: VolumeFrom) = JsString(v.mapToString())

      def read(v: JsValue) = v match {
        case JsString(s) => VolumeFrom.parse(s)
        case x => deserializationError(s"Expected volume as JsString, but got $x")
      }
    }

    implicit val capacityFormat = createEnumerationJsonFormat(Capacity)

    implicit object NetworkModeFormat extends RootJsonFormat[NetworkMode] {
      def write(m: NetworkMode) = JsString(m.mapToString())

      def read(v: JsValue) = v match {
        case JsString(s) => NetworkMode.parse(s)
        case x => deserializationError(s"Expected mode as JsString, but got $x")
      }
    }

    implicit object IpcModeFormat extends RootJsonFormat[IpcMode] {
      def write(m: IpcMode) = JsString(m.mapToString())

      def read(v: JsValue) = v match {
        case JsString(s) => IpcMode.parse(s)
        case x => deserializationError(s"Expected mode as JsString, but got $x")
      }
    }

    implicit object PidModeFormat extends RootJsonFormat[PidMode] {
      def write(m: PidMode) = JsString(m.mapToString())

      def read(v: JsValue) = v match {
        case JsString(s) => PidMode.parse(s)
        case x => deserializationError(s"Expected mode as JsString, but got $x")
      }
    }

    implicit object UtsModeFormat extends RootJsonFormat[UtsMode] {
      def write(m: UtsMode) = JsString(m.mapToString())

      def read(v: JsValue) = v match {
        case JsString(s) => UtsMode.parse(s)
        case x => deserializationError(s"Expected mode as JsString, but got $x")
      }
    }

    implicit val deviceMappingFormat =
      jsonFormat(DeviceMapping, "PathOnHost", "PathInContainer", "CgroupPermissions")

    implicit val ulimitFormat =
      jsonFormat(Ulimit, "Name", "Soft", "Hard")

    implicit val logDriverFormat = createEnumerationJsonFormat(LogDriver)

    implicit val logConfigFormat =
      jsonFormat(LogConfig, "Type", "Config")

    implicit object PortBindingsFormat extends RootJsonFormat[PortBindings] {
      def write(pb: PortBindings) = pb.map {
        case (k, v) => (k.mapToString(), v)
      }.toJson

      def read(v: JsValue) = v match {
        case JsObject(m) => m.map {
          case (k, v) => (ExposePort.parse(k), v.convertTo[List[PortBinding]])
        }
        case x => deserializationError(s"Expected bindings as JsObject, but got $x")
      }
    }

    implicit object HostConfigFormat extends RootJsonFormat[HostConfig] {
      def write(c: HostConfig) = JsObject(
        "Binds" -> c.binds.toJson,
        "Links" -> c.links.toJson,
        "LxcConf" -> c.lxcConf.toJson,
        "Memory" -> c.memory.toJson,
        "MemorySwap" -> MemorySwapFormat.write(c.memorySwap),
        "CpuShares" -> c.cpuShares.toJson,
        "CpuPeriod" -> c.cpuPeriod.toJson,
        "CpusetCpus" -> c.cpusetCpus.toJson,
        "CpusetMems" -> c.cpusetMems.toJson,
        "BlckWeight" -> c.blockIoWeight.toJson,
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
        "IpcMode" -> c.ipcMode.toJson,
        "PidMode" -> c.pidMode.toJson,
        "UTSMode" -> c.utsMode.toJson,
        "CapAdd" -> c.capacityAdd.toJson,
        "CapDrop" -> c.capacityDrop.toJson,
        "RestartPolicy" -> c.restartPolicy.toJson,
        "NetworkMode" -> c.networkMode.toJson,
        "Devices" -> c.devices.toJson,
        "Ulimits" -> c.ulimits.toJson,
        "LogConfig" -> c.logConfig.toJson,
        "SecurityOpt" -> c.securityOpt.toJson,
        "CgroupParent" -> c.cgroupParent.toJson
      )

      def read(v: JsValue) = v match {
        case obj: JsObject => HostConfig(
          binds = obj.getList[VolumeBindPath]("Binds"),
          links = obj.getList[ContainerLink]("Links"),
          lxcConf = obj.get[Map[String, String]]("LxcConf"),
          memory = obj.getOpt[Long]("Memory")(ZeroOptLongFormat),
          memorySwap = obj.getOpt[Long]("MemorySwap")(MemorySwapFormat),
          cpuShares = obj.getOpt[Int]("CpuShares")(ZeroOptIntFormat),
          cpuPeriod = obj.getOpt[Long]("CpuPeriod")(ZeroOptLongFormat),
          cpusetCpus = obj.getOpt[String]("CpusetCpus"),
          cpusetMems = obj.getOpt[String]("CpusetMems"),
          blockIoWeight = obj.getOpt[Int]("BlckWeight")(ZeroOptIntFormat),
          memorySwappiness = obj.getOpt[Int]("MemorySwappiness"),
          isOomKillDisable = obj.getOrElse("OomKillDisable", false),
          portBindings = obj.get[Map[ExposePort, List[PortBinding]]]("PortBindings"),
          isPublishAllPorts = obj.getOrElse[Boolean]("PublishAllPorts", false),
          isPrivileged = obj.getOrElse[Boolean]("Privileged", false),
          isReadonlyRootfs = obj.getOrElse[Boolean]("ReadonlyRootfs", false),
          dns = obj.getList[String]("Dns"),
          dnsSearch = obj.getList[String]("DnsSearch"),
          extraHosts = obj.getList[ExtraHost]("ExtraHosts"),
          volumesFrom = obj.getList[VolumeFrom]("VolumesFrom"),
          ipcMode = obj.getOpt[IpcMode]("IpcMode"),
          pidMode = obj.getOpt[PidMode]("PidMode"),
          utsMode = obj.getOpt[UtsMode]("UTSMode"),
          capacityAdd = obj.getList[Capacity.Capacity]("CapAdd"),
          capacityDrop = obj.getList[Capacity.Capacity]("CapDrop"),
          restartPolicy = obj.get[RestartPolicy]("RestartPolicy"),
          networkMode = obj.get[NetworkMode]("NetworkMode"),
          devices = obj.getList[DeviceMapping]("Devices"),
          ulimits = obj.getList[Ulimit]("Ulimits"),
          logConfig = obj.getOpt[LogConfig]("LogConfig"),
          securityOpt = obj.getList[String]("SecurityOpt"),
          cgroupParent = obj.getOpt[String]("CgroupParent")
        )
        case x => deserializationError(s"Expected JsObject, but got $x")
      }
    }

    implicit object ExposedPortsFormat extends RootJsonFormat[ExposedPorts] {
      def write(ep: ExposedPorts) = JsObject(ep.map { p =>
        p.mapToString -> JsObject()
      }.toMap)

      def read(v: JsValue) = v match {
        case JsObject(m) => m.keys.map(ExposePort.parse).toSet
        case x => deserializationError(s"Expected JsObject, but got $x")
      }
    }

    implicit val containerCreateFormat =
      jsonFormat(ContainerCreate.apply, "Image", "Hostname", "Domainname",
        "User", "AttachStdin", "AttachStdout", "AttachStderr", "Tty", "OpenStdin",
        "StdinOnce", "Env", "Cmd", "Entrypoint", "Labels", "Mounts",
        "NetworkDisabled", "WorkingDir", "MacAddress", "ExposedPorts", "HostConfig")

    implicit val containerCreateResponseFormat =
      jsonFormat(ContainerCreateResponse, "Id", "Warnings")

    implicit val versionFormat =
      jsonFormat(Version, "Version", "ApiVersion", "GitCommit", "GoVersion", "Os",
        "Arch", "KernelVersion", "Experimental", "BuildTime")

    implicit object ContainerStateFormat extends RootJsonReader[ContainerState] {
      def read(v: JsValue) = v match {
        case obj: JsObject => ContainerState(
          isRunning = obj.get[Boolean]("Running"),
          isPaused = obj.get[Boolean]("Paused"),
          isRestarting = obj.get[Boolean]("Restarting"),
          isOomKilled = obj.get[Boolean]("OOMKilled"),
          isDead = obj.get[Boolean]("Dead"),
          pid = obj.get[Int]("Pid"),
          exitCode = obj.get[Int]("ExitCode"),
          error = obj.getOpt[String]("Error"),
          startedAt = obj.getOpt[ZonedDateTime]("StartedAt"),
          finishedAt = obj.getOpt[ZonedDateTime]("FinishedAt")
        )
        case x => deserializationError(s"Expected JsObject, but got $x")
      }
    }

    implicit val addressFormat =
      jsonFormat(Address, "Addr", "PrefixLen")

    implicit val networkSettingsFormat =
      jsonFormat(NetworkSettings, "Bridge", "EndpointID", "Gateway", "GlobalIPv6Address",
        "GlobalIPv6PrefixLen", "HairpinMode", "IPAddress", "IPPrefixLen", "IPv6Gateway",
        "LinkLocalIPv6Address", "LinkLocalIPv6PrefixLen", "MacAddress", "NetworkID", "Ports",
        "SandboxKey", "SecondaryIPAddresses", "SecondaryIPv6Addresses")

    implicit object ContainerBaseFormat extends RootJsonReader[ContainerBase] {
      def read(v: JsValue) = v match {
        case obj: JsObject => ContainerBase(
          id = obj.get[String]("Id"),
          createdAt = obj.get[ZonedDateTime]("Created"),
          path = obj.get[String]("Path"),
          args = obj.getList[String]("Args"),
          state = obj.get[ContainerState]("State"),
          image = obj.get[String]("Image"),
          networkSettings = obj.get[NetworkSettings]("NetworkSettings"),
          resolvConfPath = obj.getOpt[String]("ResolvConfPath"),
          hostnamePath = obj.getOpt[String]("HostnamePath"),
          hostsPath = obj.getOpt[String]("HostsPath"),
          logPath = obj.getOpt[String]("LogPath"),
          name = obj.get[String]("Name"),
          restartCount = obj.get[Int]("RestartCount"),
          driver = obj.get[String]("Driver"),
          execDriver = obj.get[String]("ExecDriver"),
          mountLabel = obj.getOpt[String]("MountLabel"),
          processLabel = obj.getOpt[String]("ProcessLabel"),
          volumes = obj.get[Map[String, String]]("Volumes"),
          volumesRw = obj.get[Map[String, Boolean]]("VolumesRW"),
          appArmorProfile = obj.getOpt[String]("AppArmorProfile"),
          execIds = obj.getList[String]("ExecIDs"),
          hostConfig = obj.get[HostConfig]("HostConfig")
        // GraphDriver     GraphDriverData
        )
        case x => deserializationError(s"Expected JsObject, but got $x")
      }
    }
  }
}
