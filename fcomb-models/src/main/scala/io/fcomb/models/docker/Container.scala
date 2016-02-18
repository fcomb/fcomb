package io.fcomb.models.docker

import io.fcomb.models.ModelWithAutoLongPk
import scala.collection.immutable
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

  def parseDockerStatus(status: String): ContainerState =
    status.toLowerCase match {
      case s if s.startsWith("up")         ⇒ Running
      case s if s.startsWith("restarting") ⇒ Restarting
      case s if s.startsWith("removal")    ⇒ Terminating
      case s if s.startsWith("created")    ⇒ Created
      case _                               ⇒ Stopped
    }
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

  def isPending =
    isPresent && state == ContainerState.Pending

  private val inProgressSet = immutable.HashSet(
    ContainerState.Pending, ContainerState.Starting, ContainerState.Stopping,
    ContainerState.Restarting, ContainerState.Terminating
  )

  def isInProgress =
    inProgressSet.contains(this.state)
}
