package io.fcomb.models.docker

import io.fcomb.models.ModelWithAutoLongPk
import java.time.ZonedDateTime

object ContainerState extends Enumeration {
  type ContainerState = Value

  val Initializing = Value("initializing")
  val Starting = Value("starting")
  val Running = Value("running")
  val Stopping = Value("stopping")
  val Stopped = Value("stopped")
  val Terminating = Value("terminating")
  val Terminated = Value("terminated")
}

sealed trait Container {
  val id: Option[Long]
  val state: ContainerState.ContainerState
  val userId: Long
  val applicationId: Long
  val nodeId: Long
  val name: String
  val createdAt: ZonedDateTime
  val terminatedAt: Option[ZonedDateTime]
}

case class DockerContainer(
    id:            Option[Long]                  = None,
    state:         ContainerState.ContainerState,
    userId:        Long,
    applicationId: Long,
    nodeId:        Long,
    name:          String,
    createdAt:     ZonedDateTime,
    terminatedAt:  Option[ZonedDateTime]         = None
) extends Container with ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))

  def dockerName() = s"${getId()}_$name"
}
