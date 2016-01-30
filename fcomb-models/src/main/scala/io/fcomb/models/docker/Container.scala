package io.fcomb.models.docker

import io.fcomb.models.ModelWithAutoLongPk
import java.time.ZonedDateTime

object ContainerState extends Enumeration {
  type ContainerState = Value

  val Pending = Value("pending")
  val Created = Value("created")
  val Starting = Value("starting")
  val Running = Value("running")
  val Stopping = Value("stopping")
  val Stopped = Value("stopped")
  val Restarting = Value("restarting")
  val Unreachable = Value("unreachable")
  val Terminating = Value("terminating")
  val Terminated = Value("terminated")
}

case class Container(
    id:            Option[Long]                  = None,
    state:         ContainerState.ContainerState,
    userId:        Long,
    applicationId: Long,
    nodeId:        Long,
    name:          String,
    number:        Int,
    dockerId:      Option[String]                = None,
    createdAt:     ZonedDateTime,
    updatedAt:     ZonedDateTime,
    terminatedAt:  Option[ZonedDateTime]         = None
) extends ModelWithAutoLongPk {
  def withPk(id: Long) = this.copy(id = Some(id))

  def dockerName() = s"${name}_${getId()}"

  def isTerminated =
    state == ContainerState.Terminated

  def isUnreachable =
    state == ContainerState.Unreachable

  def isPresent =
    state != ContainerState.Pending &&
      !isTerminated &&
      !isUnreachable &&
      dockerId.nonEmpty

  def isRunning =
    isPresent && state == ContainerState.Running

  def isNotRunning =
    isPresent && state == ContainerState.Stopped

  def isPending =
    isPresent && state == ContainerState.Pending
}
