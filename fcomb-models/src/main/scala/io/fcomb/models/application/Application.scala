package io.fcomb.models.application

import io.fcomb.models.ModelWithAutoLongPk
import java.time.ZonedDateTime

object ApplicationState extends Enumeration {
  type ApplicationState = Value

  val Initializing = Value("initializing")
  val Starting = Value("starting")
  val Running = Value("running")
  val PartlyRunning = Value("partly_running")
  val Stopping = Value("stopping")
  val Stopped = Value("stopped")
  val Deploying = Value("deploying")
  val Scaling = Value("scaling")
  val Terminating = Value("terminating")
  val Terminated = Value("terminated")
}

@SerialVersionUID(1L)
case class Application(
    id:            Option[Long]                      = None,
    userId:        Long,
    state:         ApplicationState.ApplicationState,
    name:          String,
    image:         DockerImage,
    deployOptions: DockerDeployOptions,
    createdAt:     ZonedDateTime,
    updatedAt:     ZonedDateTime
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))
}

sealed trait Image

sealed trait ContainerImage extends Image

@SerialVersionUID(1L)
case class DockerImage(
  name:     String,
  tag:      Option[String] = None,
  registry: Option[String] = None
) extends ContainerImage

sealed trait DeployOptions

sealed trait ContainerDeployOptions extends DeployOptions

object NetworkPort extends Enumeration {
  type NetworkPort = Value

  val Tcp = Value("tcp")
  val Udp = Value("udp")
}

case class DockerDeployPort(
  port: Int,
  protocol: NetworkPort.NetworkPort,
  isPublished: Boolean,
  bindPort: Option[Int],
  bindInterface: Option[String]
)

@SerialVersionUID(1L)
case class DockerDeployOptions(
  ports:         Set[DockerDeployPort],
  isAutoRestart: Boolean,
  isAutoDestroy: Boolean,
  isPrivileged:  Boolean,
  // pid: ...,
  // network: ....
  command:     Option[String],
  entrypoint:  Option[String],
  memoryLimit: Option[Long],
  cpuShares:   Option[Long]
) extends ContainerDeployOptions
