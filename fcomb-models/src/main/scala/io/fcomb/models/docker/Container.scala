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

case class Container(
    id:            Option[Long]                  = None,
    state:         ContainerState.ContainerState,
    userId:        Long,
    applicationId: Long,
    nodeId:        Option[Long],
    name:          String,
    number:        Int,
    dockerId:      Option[String]                = None,
    createdAt:     ZonedDateTime,
    updatedAt:     ZonedDateTime,
    terminatedAt:  Option[ZonedDateTime]         = None
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))

  def dockerName() = s"$name.${getId()}"

  def isTerminated() =
    state == ContainerState.Terminated ||
      state == ContainerState.Terminating
}
