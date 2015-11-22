package io.fcomb.docker.api.json

import io.fcomb.docker.api.methods.ContainerMethods._
import spray.json._
import spray.json.DefaultJsonProtocol.{listFormat => _, _}
import io.fcomb.json._
import java.time.{LocalDateTime, ZonedDateTime}

private[api] object ContainerMethodsFormat {
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
        labels = obj.get[Map[String, String]]("Labels"),
        isExperimentalBuild = obj.getOrElse[Boolean]("ExperimentalBuild", false)
      )
      case x => deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit val portKindFormat =
    createStringEnumJsonFormat(PortKind)

  implicit object PortFormat extends RootJsonFormat[Port] {
    def write(p: Port) = JsObject(
      "PrivatePort" -> p.privatePort.toJson,
      "PublicPort" -> p.publicPort.toJson,
      "Type" -> p.kind.toJson,
      "IP" -> p.ip.toJson
    )

    def read(v: JsValue) = v match {
      case obj: JsObject => Port(
        privatePort = obj.get[Int]("PrivatePort").toInt,
        publicPort = obj.getOpt[Int]("PublicPort"),
        kind = obj.get[PortKind.PortKind]("Type"),
        ip = obj.getOpt[String]("IP")
      )
      case x => deserializationError(s"Expected port as JsObject, but got $x")
    }
  }

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

  implicit object PortBindingFormat extends RootJsonFormat[PortBinding] {
    def write(pb: PortBinding) = JsObject(
      "HostPort" -> JsString(pb.port.toString),
      "HostIp" -> pb.ip.toJson
    )

    def read(v: JsValue) = v match {
      case obj: JsObject => PortBinding(
        port = obj.get[String]("HostPort").toInt,
        ip = obj.getOpt[String]("HostIp")
      )
      case x => deserializationError(s"Expected port binding as JsObject, but got $x")
    }
  }

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

  implicit val capacityFormat = createStringEnumJsonFormat(Capacity)

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

  implicit val logDriverFormat = createStringEnumJsonFormat(LogDriver)

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

  implicit object ConsoleSizeFormat extends RootJsonFormat[ConsoleSize] {
    def write(s: ConsoleSize) =
      JsArray(List(JsNumber(s.width), JsNumber(s.height)))

    def read(v: JsValue) = v match {
      case JsArray(Vector(JsNumber(w), JsNumber(h))) =>
        ConsoleSize(w.toInt, h.toInt)
      case x => deserializationError(s"Expected size as JsArray, but got $x")
    }
  }

  implicit val isolationLevelFormat = createStringEnumJsonFormat(IsolationLevel)

  object LxcConfFormat extends RootJsonFormat[LxcConf] {
    def write(c: LxcConf) = c.toJson

    def read(v: JsValue) = v match {
      case JsArray(xs: Vector[JsObject]) => xs.map { item =>
        (item.get[String]("Key"), item.get[String]("Value"))
      }.toMap
      case JsNull => Map.empty
      case x => deserializationError(s"Expected conf as JsArray, but got $x")
    }
  }

  implicit object HostConfigFormat extends RootJsonFormat[HostConfig] {
    def write(c: HostConfig) = JsObject(
      "Binds" -> c.binds.toJson,
      "Links" -> c.links.toJson,
      "LxcConf" -> LxcConfFormat.write(c.lxcConf),
      "Memory" -> c.memory.toJson,
      "MemorySwap" -> MemorySwapFormat.write(c.memorySwap),
      "KernelMemory" -> c.kernelMemory.toJson,
      "CpuShares" -> c.cpuShares.toJson,
      "CpuPeriod" -> c.cpuPeriod.toJson,
      "CpusetCpus" -> c.cpusetCpus.toJson,
      "CpusetMems" -> c.cpusetMems.toJson,
      "CpuQuota" -> c.cpuQuota.toJson,
      "BlkioWeight" -> c.blockIoWeight.toJson,
      "MemorySwappiness" -> c.memorySwappiness.toJson,
      "OomKillDisable" -> c.isOomKillDisable.toJson,
      "PortBindings" -> c.portBindings.toJson,
      "PublishAllPorts" -> c.isPublishAllPorts.toJson,
      "Privileged" -> c.isPrivileged.toJson,
      "ReadonlyRootfs" -> c.isReadonlyRootfs.toJson,
      "Dns" -> c.dns.toJson,
      "DnsOptions" -> c.dnsOptions.toJson,
      "DnsSearch" -> c.dnsSearch.toJson,
      "ExtraHosts" -> c.extraHosts.toJson,
      "VolumesFrom" -> c.volumesFrom.toJson,
      "IpcMode" -> c.ipcMode.toJson,
      "PidMode" -> c.pidMode.toJson,
      "UTSMode" -> c.utsMode.toJson,
      "CapAdd" -> c.capacityAdd.toJson,
      "CapDrop" -> c.capacityDrop.toJson,
      "GroupAdd" -> c.groupAdd.toJson,
      "RestartPolicy" -> c.restartPolicy.toJson,
      "NetworkMode" -> c.networkMode.toJson,
      "Devices" -> c.devices.toJson,
      "Ulimits" -> c.ulimits.toJson,
      "LogConfig" -> c.logConfig.toJson,
      "SecurityOpt" -> c.securityOpt.toJson,
      "CgroupParent" -> c.cgroupParent.toJson,
      "ConsoleSize" -> c.consoleSize.toJson,
      "VolumeDriver" -> c.volumeDriver.toJson,
      "Isolation" -> c.isolation.toJson
    )

    def read(v: JsValue) = v match {
      case obj: JsObject => HostConfig(
        binds = obj.getList[VolumeBindPath]("Binds"),
        links = obj.getList[ContainerLink]("Links"),
        lxcConf = obj.get[Map[String, String]]("LxcConf")(LxcConfFormat),
        memory = obj.getOpt[Long]("Memory")(ZeroOptLongFormat),
        memorySwap = obj.getOpt[Long]("MemorySwap")(MemorySwapFormat),
        kernelMemory = obj.getOpt[Long]("KernelMemory")(ZeroOptLongFormat),
        cpuShares = obj.getOpt[Int]("CpuShares")(ZeroOptIntFormat),
        cpuPeriod = obj.getOpt[Long]("CpuPeriod")(ZeroOptLongFormat),
        cpusetCpus = obj.getOpt[String]("CpusetCpus"),
        cpusetMems = obj.getOpt[String]("CpusetMems"),
        cpuQuota = obj.getOpt[Long]("CpuQuota")(ZeroOptLongFormat),
        blockIoWeight = obj.getOpt[Int]("BlkioWeight")(ZeroOptIntFormat),
        memorySwappiness = obj.getOpt[Int]("MemorySwappiness"),
        isOomKillDisable = obj.getOrElse("OomKillDisable", false),
        portBindings = obj.get[Map[ExposePort, List[PortBinding]]]("PortBindings"),
        isPublishAllPorts = obj.getOrElse[Boolean]("PublishAllPorts", false),
        isPrivileged = obj.getOrElse[Boolean]("Privileged", false),
        isReadonlyRootfs = obj.getOrElse[Boolean]("ReadonlyRootfs", false),
        dns = obj.getList[String]("Dns"),
        dnsOptions = obj.getList[String]("DnsOptions"),
        dnsSearch = obj.getList[String]("DnsSearch"),
        extraHosts = obj.getList[ExtraHost]("ExtraHosts"),
        volumesFrom = obj.getList[VolumeFrom]("VolumesFrom"),
        ipcMode = obj.getOpt[IpcMode]("IpcMode"),
        pidMode = obj.getOpt[PidMode]("PidMode"),
        utsMode = obj.getOpt[UtsMode]("UTSMode"),
        capacityAdd = obj.getList[Capacity.Capacity]("CapAdd"),
        capacityDrop = obj.getList[Capacity.Capacity]("CapDrop"),
        groupAdd = obj.getList[String]("GroupAdd"),
        restartPolicy = obj.get[RestartPolicy]("RestartPolicy"),
        networkMode = obj.get[NetworkMode]("NetworkMode"),
        devices = obj.getList[DeviceMapping]("Devices"),
        ulimits = obj.getList[Ulimit]("Ulimits"),
        logConfig = obj.getOpt[LogConfig]("LogConfig"),
        securityOpt = obj.getList[String]("SecurityOpt"),
        cgroupParent = obj.getOpt[String]("CgroupParent"),
        consoleSize = obj.getOpt[ConsoleSize]("ConsoleSize"),
        volumeDriver = obj.getOpt[String]("VolumeDriver"),
        isolation = obj.getOpt[IsolationLevel.IsolationLevel]("Isolation")
      )
      case x => deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit val containerProcessListFormat =
    jsonFormat(ContainerProcessList, "Processes", "Titles")

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

  implicit object VersionFormat extends RootJsonReader[Version] {
    def read(v: JsValue) = v match {
      case obj: JsObject => Version(
        version = obj.get[String]("Version"),
        apiVersion = obj.get[String]("ApiVersion"),
        gitCommit = obj.get[String]("GitCommit"),
        goVersion = obj.get[String]("GoVersion"),
        os = obj.get[String]("Os"),
        arch = obj.get[String]("Arch"),
        kernelVersion = obj.getOpt[String]("KernelVersion"),
        experimental = obj.getOpt[Boolean]("Experimental"),
        buildTime = obj.getOpt[String]("BuildTime")
      )
      case x => deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit object ContainerStateFormat extends RootJsonReader[ContainerState] {
    def read(v: JsValue) = v match {
      case obj: JsObject => ContainerState(
        isRunning = obj.get[Boolean]("Running"),
        isPaused = obj.get[Boolean]("Paused"),
        isRestarting = obj.get[Boolean]("Restarting"),
        isOomKilled = obj.get[Boolean]("OOMKilled"),
        isDead = obj.get[Boolean]("Dead"),
        pid = obj.getOpt[Int]("Pid")(ZeroOptIntFormat),
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

  implicit object RunConfigFormat extends RootJsonReader[RunConfig] {
    def read(v: JsValue) = v match {
      case obj: JsObject => RunConfig(
        hostname = obj.getOpt[String]("Hostname"),
        domainName = obj.getOpt[String]("Domainname"),
        user = obj.getOpt[String]("User"),
        isAttachStdin = obj.getOrElse[Boolean]("AttachStdin", false),
        isAttachStdout = obj.getOrElse[Boolean]("AttachStdout", false),
        isAttachStderr = obj.getOrElse[Boolean]("AttachStderr", false),
        exposedPorts = obj.get[ExposedPorts]("ExposedPorts"),
        publishService = obj.getOpt[String]("PublishService"),
        isTty = obj.getOrElse[Boolean]("Tty", false),
        isOpenStdin = obj.getOrElse[Boolean]("OpenStdin", false),
        isStdinOnce = obj.getOrElse[Boolean]("StdinOnce", false),
        env = obj.getList[String]("Env"),
        command = obj.getList[String]("Cmd"),
        image = obj.get[String]("Image"),
        volumes = obj.get[Map[String, Unit]]("Volumes"),
        volumeDriver = obj.getOpt[String]("VolumeDriver"),
        workingDirectory = obj.getOpt[String]("WorkingDir"),
        entrypoint = obj.getList[String]("Entrypoint"),
        isNetworkDisabled = obj.getOrElse[Boolean]("NetworkDisabled", false),
        macAddress = obj.getOpt[String]("MacAddress"),
        labels = obj.get[Map[String, String]]("Labels"),
        onBuild = obj.getList[String]("OnBuild")
      )
      case x => deserializationError(s"Expected JsObject, but got $x")
    }
  }

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
        hostConfig = obj.get[HostConfig]("HostConfig"),
        // GraphDriver     GraphDriverData
        config = obj.get[RunConfig]("Config")
      )
      case x => deserializationError(s"Expected JsObject, but got $x")
    }
  }

  implicit val fileChangeKindFormat =
    createIntEnumJsonFormat(FileChangeKind)

  implicit val fileChangeFormat =
    jsonFormat(FileChange, "Path", "Kind")

  implicit object ContainerChangesFormat extends RootJsonReader[ContainerChanges] {
    def read(v: JsValue) = ContainerChanges(v.convertTo[List[FileChange]])
  }

  implicit val networkStatsFormat =
    jsonFormat(NetworkStats, "rx_bytes", "rx_packets",
      "rx_errors", "rx_dropped", "tx_bytes", "tx_packets", "tx_errors", "tx_dropped")

  implicit val cpuUsageFormat =
    jsonFormat(CpuUsage, "total_usage", "percpu_usage",
      "usage_in_kernelmode", "usage_in_usermode")

  implicit val throttlingDataFormat =
    jsonFormat(ThrottlingData, "periods", "throttled_periods", "throttled_time")

  implicit val cpuStatsFormat =
    jsonFormat(CpuStats, "cpu_usage", "system_cpu_usage", "throttling_data")

  implicit val memoryStatsFormat =
    jsonFormat(MemoryStats, "usage", "max_usage", "stats", "failcnt", "limit")

  implicit val blockIoStatEntryFormat =
    jsonFormat(BlockIoStatEntry, "major", "minor", "op", "value")

  implicit val blockIoStatsFormat =
    jsonFormat(BlockIoStats, "io_service_bytes_recursive", "io_serviced_recursive",
      "io_queue_recursive", "io_service_time_recursive", "io_wait_time_recursive",
      "io_merged_recursive", "io_time_recursive", "sectors_recursive")

  implicit val containerStatsFormat =
    jsonFormat(ContainerStats, "read", "precpu_stats",
      "cpu_stats", "memory_stats", "blkio_stats", "networks", "network")

  implicit val containerWaitResponseFormat =
    jsonFormat(ContainerWaitResponse, "StatusCode")

  implicit val copyConfigFormat =
    jsonFormat(CopyConfig, "Resource")

  implicit val containerPathStatFormat =
    jsonFormat(ContainerPathStat, "name", "size", "mode", "mtime", "linkTarget")
}
