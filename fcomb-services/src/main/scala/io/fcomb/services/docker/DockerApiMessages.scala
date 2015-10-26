package io.fcomb.services.docker

import spray.json._
import io.fcomb.json

object DockerApiMessages {
  sealed trait DockerApiResponse

  sealed trait DockerApiRequest

  case class DockerApiInfo(
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

  case class DockerApiContainerItem(
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

  case class DockerApiContainerCreate(
    hostname: Option[String],
    domainName: Option[String],
    user: Option[String],
    isAttachStdin: Boolean,
    isAttachStdout: Boolean,
    isAttachStderr: Boolean,
    isTty: Boolean,
    isOpenStdin: Boolean,
    isStdinOnce: Boolean,
    env: Map[String, String],
    command: List[String],
    entrypoint: Option[String],
    image: String,
    labels: Map[String, String],
    mounts: List[MountPoint],
    workingDirectory: Option[String],
    macAddress: String,
    // exposedPorts: Map[String],
    hostConfig: HostConfig
  ) extends DockerApiRequest

  object JsonProtocols extends DefaultJsonProtocol {
    implicit val dockerApiInfoFormat =
      jsonFormat(DockerApiInfo, "ID", "Containers", "Images",
        "Driver", "DriverStatus", "MemoryLimit", "SwapLimit", "CpuCfsPeriod",
        "CpuCfsQuota", "IPv4Forwarding", "KernelVersion", "OperatingSystem",
        "NCPU", "MemTotal", "Name")

    implicit val containerPortKindFormat =
      json.createEnumerationJsonFormat(ContainerPortKind)

    implicit val containerPortFormat =
      jsonFormat(ContainerPort, "PrivatePort", "PublicPort", "Type", "IP")

    implicit val dockerApiContainerItemFormat =
      jsonFormat(DockerApiContainerItem, "Id", "Names", "Image", "Command", "Created",
        "Status", "Ports", "SizeRw", "SizeRootFs")
  }
}
