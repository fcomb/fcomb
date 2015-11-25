package io.fcomb.docker.api.methods

import spray.json.deserializationError
import java.time.{LocalDateTime, ZonedDateTime}

object ContainerMethods {
  final case class ContainerState(
    isRunning: Boolean,
    isPaused: Boolean,
    isRestarting: Boolean,
    isOomKilled: Boolean,
    isDead: Boolean,
    pid: Option[Int],
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

  final case class Port(
    privatePort: Int,
    publicPort: Option[Int],
    kind: PortKind.PortKind,
    ip: Option[String]
  )

  final case class ContainerItem(
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

  final case class MountPoint(
    source: String,
    destination: String,
    mode: Set[MountMode.MountMode],
    isReadAndWrite: Boolean
  )

  final case class PortBinding(
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

  final case class HostConfigNetworkMode(
    networkMode: String
  )

  final case class Container(
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

  final case class CopyConfig(
    resource: String
  ) extends DockerApiRequest

  final case class ContainerPathStat(
    name: String,
    size: Long,
    mode: Int, // TODO
    modifiedAt: ZonedDateTime,
    linkTarget: Option[String]
  )

  final case class ContainerProcessList(
    processes: List[List[String]],
    titles: List[String]
  )

  sealed trait VolumeBindPath extends MapToString

  object VolumeBindPath {
    final case class VolumePath(path: String) extends VolumeBindPath {
      def mapToString() = path
    }

    final case class VolumeHostPath(
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

  final case class ContainerLink(
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

  final case class VolumeFrom(
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

  final case class ExtraHost(
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

  object Signal extends Enumeration {
    type Signal = Value

    val ABRT = Value("ABRT")
    val ALRM = Value("ALRM")
    val BUS = Value("BUS")
    val CHLD = Value("CHLD")
    val CLD = Value("CLD")
    val CONT = Value("CONT")
    val FPE = Value("FPE")
    val HUP = Value("HUP")
    val ILL = Value("ILL")
    val INT = Value("INT")
    val IO = Value("IO")
    val IOT = Value("IOT")
    val KILL = Value("KILL")
    val PIPE = Value("PIPE")
    val POLL = Value("POLL")
    val PROF = Value("PROF")
    val PWR = Value("PWR")
    val QUIT = Value("QUIT")
    val SEGV = Value("SEGV")
    val STKFLT = Value("STKFLT")
    val STOP = Value("STOP")
    val SYS = Value("SYS")
    val TERM = Value("TERM")
    val TRAP = Value("TRAP")
    val TSTP = Value("TSTP")
    val TTIN = Value("TTIN")
    val TTOU = Value("TTOU")
    val UNUSED = Value("UNUSED")
    val URG = Value("URG")
    val USR1 = Value("USR1")
    val USR2 = Value("USR2")
    val VTALRM = Value("VTALRM")
    val WINCH = Value("WINCH")
    val XCPU = Value("XCPU")
    val XFSZ = Value("XFSZ")
  }

  object Capacity extends Enumeration {
    type Capacity = Value

    val SetPcap = Value("SETPCAP")
    val SysModule = Value("SYS_MODULE")
    val SysRawIo = Value("SYS_RAWIO")
    val SysPacct = Value("SYS_PACCT")
    val SysAdmib = Value("SYS_ADMIN")
    val SysNice = Value("SYS_NICE")
    val SysResource = Value("SYS_RESOURCE")
    val SysTime = Value("SYS_TIME")
    val SysTtyConfig = Value("SYS_TTY_CONFIG")
    val Mknod = Value("MKNOD")
    val AuditWrite = Value("AUDIT_WRITE")
    val AuditControl = Value("AUDIT_CONTROL")
    val MacOverride = Value("MAC_OVERRIDE")
    val MacAdmin = Value("MAC_ADMIN")
    val NetAdmin = Value("NET_ADMIN")
    val Syslog = Value("SYSLOG")
    val Chown = Value("CHOWN")
    val NetRaw = Value("NET_RAW")
    val DacOverride = Value("DAC_OVERRIDE")
    val Fowner = Value("FOWNER")
    val DacReadSearch = Value("DAC_READ_SEARCH")
    val Fsetid = Value("FSETID")
    val Kill = Value("KILL")
    val Setgid = Value("SETGID")
    val Setuid = Value("SETUID")
    val LinuxImmutable = Value("LINUX_IMMUTABLE")
    val NetBindService = Value("NET_BIND_SERVICE")
    val NetBroadcast = Value("NET_BROADCAST")
    val IpcLock = Value("IPC_LOCK")
    val IpcOwner = Value("IPC_OWNER")
    val SysChroot = Value("SYS_CHROOT")
    val SysPtrace = Value("SYS_PTRACE")
    val SysBoot = Value("SYS_BOOT")
    val Lease = Value("LEASE")
    val Setfcap = Value("SETFCAP")
    val WakeAlarm = Value("WAKE_ALARM")
    val BlockSuspend = Value("BLOCK_SUSPEND")
    val AuditRead = Value("AUDIT_READ")
  }

  sealed trait NetworkMode extends MapToString

  object NetworkMode {
    case object Bridge extends NetworkMode {
      def mapToString() = "bridge"
    }

    case object Host extends NetworkMode {
      def mapToString() = "host"
    }

    final case class ContainerHost(name: String) extends NetworkMode {
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

    final case class ContainerHost(name: String) extends IpcMode {
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

  final case class DeviceMapping(
    pathOnHost: String,
    pathInContainer: String,
    cgroupPermissions: String
  )

  final case class Ulimit(
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

  final case class LogConfig(
    kind: LogDriver.LogDriver,
    config: Map[String, String] = Map.empty
  )

  type PortBindings = Map[ExposePort, List[PortBinding]]

  object IsolationLevel extends Enumeration {
    type IsolationLevel = Value

    val Default = Value("default")
    val HyperV = Value("hyperv")
  }

  final case class ConsoleSize(
    width: Int,
    height: Int
  )

  type LxcConf = Map[String, String]

  final case class HostConfig(
    binds: List[VolumeBindPath] = List.empty,
    links: List[ContainerLink] = List.empty,
    lxcConf: LxcConf = Map.empty,
    memory: Option[Long] = None,
    memorySwap: Option[Long] = None,
    kernelMemory: Option[Long] = None,
    cpuShares: Option[Int] = None,
    cpuPeriod: Option[Long] = None,
    cpusetCpus: Option[String] = None,
    cpusetMems: Option[String] = None,
    cpuQuota: Option[Long] = None,
    blockIoWeight: Option[Int] = None,
    memorySwappiness: Option[Int] = None,
    isOomKillDisable: Boolean = false,
    portBindings: PortBindings = Map.empty,
    isPublishAllPorts: Boolean = false,
    isPrivileged: Boolean = false,
    isReadonlyRootfs: Boolean = false,
    dns: List[String] = List.empty,
    dnsOptions: List[String] = List.empty,
    dnsSearch: List[String] = List.empty,
    extraHosts: List[ExtraHost] = List.empty,
    volumesFrom: List[VolumeFrom] = List.empty,
    ipcMode: Option[IpcMode] = None,
    pidMode: Option[PidMode] = None,
    utsMode: Option[UtsMode] = None,
    capacityAdd: List[Capacity.Capacity] = List.empty,
    capacityDrop: List[Capacity.Capacity] = List.empty,
    groupAdd: List[String] = List.empty,
    restartPolicy: RestartPolicy = RestartPolicy.No,
    networkMode: NetworkMode = NetworkMode.Bridge,
    devices: List[DeviceMapping] = List.empty,
    ulimits: List[Ulimit] = List.empty,
    logConfig: Option[LogConfig] = None,
    securityOpt: List[String] = List.empty,
    cgroupParent: Option[String] = None,
    consoleSize: Option[ConsoleSize] = None,
    volumeDriver: Option[String] = None,
    isolation: Option[IsolationLevel.IsolationLevel] = None
  )

  sealed trait ExposePort extends MapToString {
    val port: Int
    val kind: String

    def mapToString() = s"$port/$kind"
  }

  object ExposePort {
    final case class Tcp(port: Int) extends ExposePort {
      val kind = "tcp"
    }

    final case class Udp(port: Int) extends ExposePort {
      val kind = "udp"
    }

    def parse(s: String) = s.split('/').toList match {
      case p :: "tcp" :: Nil => Tcp(p.toInt)
      case p :: "udp" :: Nil => Udp(p.toInt)
      case _ => deserializationError(s"Unknown port and protocol: $s")
    }
  }

  type ExposedPorts = Set[ExposePort]

  final case class RunConfig(
    hostname: Option[String] = None,
    domainName: Option[String] = None,
    user: Option[String] = None,
    isAttachStdin: Boolean = false,
    isAttachStdout: Boolean = false,
    isAttachStderr: Boolean = false,
    exposedPorts: ExposedPorts = Set.empty,
    publishService: Option[String] = None,
    isTty: Boolean = false,
    isOpenStdin: Boolean = false,
    isStdinOnce: Boolean = false,
    env: List[String] = List.empty,
    command: List[String] = List.empty,
    image: Option[String],
    volumes: Map[String, Unit] = Map.empty,
    volumeDriver: Option[String] = None,
    workingDirectory: Option[String] = None,
    entrypoint: List[String] = List.empty,
    isNetworkDisabled: Boolean = false,
    macAddress: Option[String] = None,
    labels: Map[String, String] = Map.empty,
    onBuild: List[String] = List.empty
  )

  final case class ContainerCreate(
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

  final case class Address(
    address: String,
    prefixLength: Int
  )

  final case class NetworkSettings(
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

  final case class ContainerBase(
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
    hostConfig: HostConfig,
    // GraphDriver     GraphDriverData
    config: RunConfig
  ) extends DockerApiResponse

  final case class ContainerCreateResponse(
    id: String,
    warnings: List[String]
  ) extends DockerApiResponse

  final case class ContainerExecCreateResponse(
    id: String
  ) extends DockerApiResponse

  final case class AuthResponse(
    status: String
  ) extends DockerApiResponse

  final case class ContainerWaitResponse(
    statusCode: Int
  ) extends DockerApiResponse

  final case class ContainerCommitResponse(
    id: String
  ) extends DockerApiResponse

  final case class ContainerChange(
    kind: Int,
    path: String
  ) extends DockerApiResponse

  final case class GraphDriverData(
    name: String,
    data: Map[String, String]
  )

  object FileChangeKind extends Enumeration {
    type FileChangeKind = Value

    val Modified = Value(0)
    val Added = Value(1)
    val Deleted = Value(2)
  }

  final case class FileChange(
    path: String,
    kind: FileChangeKind.FileChangeKind
  )

  final case class ContainerChanges(
    changes: List[FileChange]
  ) extends DockerApiResponse

  final case class NetworkStats(
    rxBytes: Long,
    rxPackets: Long,
    rxErrors: Long,
    rxDropped: Long,
    txBytes: Long,
    txPackets: Long,
    txErrors: Long,
    txDropped: Long
  )

  final case class ThrottlingData(
    periods: Long,
    throttledPeriods: Long,
    throttledTime: Long
  )

  final case class CpuUsage(
    total: Long,
    perCpu: List[Long],
    inKernelMode: Long,
    inUserMode: Long
  )

  final case class CpuStats(
    cpu: CpuUsage,
    system: Long,
    throttling: ThrottlingData
  )

  final case class MemoryStats(
    usage: Long,
    maxUsage: Long,
    stats: Map[String, Long],
    failcnt: Long,
    limit: Long
  )

  final case class BlockIoStatEntry(
    major: Long,
    minor: Long,
    op: Option[String],
    value: Long
  )

  final case class BlockIoStats(
    ioServiceBytes: List[BlockIoStatEntry],
    ioServiced: List[BlockIoStatEntry],
    ioQueued: List[BlockIoStatEntry],
    ioServiceTime: List[BlockIoStatEntry],
    ioWaitTime: List[BlockIoStatEntry],
    ioMerged: List[BlockIoStatEntry],
    ioTime: List[BlockIoStatEntry],
    sectors: List[BlockIoStatEntry]
  )

  final case class ContainerStats(
    readedAt: ZonedDateTime,
    preCpu: CpuStats,
    cpu: CpuStats,
    memory: MemoryStats,
    blockIo: BlockIoStats,
    networks: Option[Map[String, NetworkStats]],
    network: Option[NetworkStats]
  ) extends DockerApiResponse

  final case class ExecConfig(
    user: Option[String] = None,
    isPrivileged: Boolean = false,
    isTty: Boolean = false,
    containerId: Option[String] = None,
    isAttachStdin: Boolean = false,
    isAttachStderr: Boolean = false,
    isAttachStdout: Boolean = false,
    isDetach: Boolean = false,
    command: List[String]
  )

  final case class ExecStartCheck(
    isDetach: Boolean,
    isTty: Boolean
  ) extends DockerApiRequest

  final case class ProcessConfig(
    isPrivileged: Boolean,
	  user: Option[String],
	  isTty: Boolean,
	  entrypoint: Option[String],
	  arguments: List[String]
  )

  final case class ExecInspect(
    id: String,
	  isRunning: Boolean,
	  exitCode: Option[Int],
	  processConfig: ProcessConfig,
	  isOpenStdin: Boolean,
	  isOpenStderr: Boolean,
	  isOpenStdout: Boolean,
	  container: ContainerBase
  ) extends DockerApiResponse
}
