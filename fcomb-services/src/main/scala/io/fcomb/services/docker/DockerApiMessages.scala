package io.fcomb.services.docker

import spray.json._
import spray.json.DefaultJsonProtocol._
import io.fcomb.json._

object DockerApiMessages {
  sealed trait DockerApiMessage

  sealed trait DockerApiResponse extends DockerApiMessage

  sealed trait DockerApiRequest extends DockerApiMessage

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
    kernelVersion: String,
    operatingSystem: String,
    cpus: Int,
    memory: Long,
    name: String
  ) extends DockerApiResponse

  object ContainerPortKind extends Enumeration {
    type ContainerPortKind = Value

    val Tcp = Value("tcp")
    val Udp = Value("udp")
  }

  case class ContainerPort(
    privatePort: Int,
    publicPort: Option[Int],
    kind: ContainerPortKind.ContainerPortKind,
    ip: Option[String]
  )

  case class ContainerItem(
    id: String,
    names: List[String],
    image: String,
    command: String,
    created: Long,
    status: String,
    ports: List[ContainerPort],
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

  object JsonProtocols {
    implicit val infoFormat =
      jsonFormat(Info, "ID", "Containers", "Images",
        "Driver", "DriverStatus", "MemoryLimit", "SwapLimit", "CpuCfsPeriod",
        "CpuCfsQuota", "IPv4Forwarding", "KernelVersion", "OperatingSystem",
        "NCPU", "MemTotal", "Name")

    implicit val containerPortKindFormat =
      createEnumerationJsonFormat(ContainerPortKind)

    implicit val containerPortFormat =
      jsonFormat(ContainerPort, "PrivatePort", "PublicPort", "Type", "IP")

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
  }
}
