package io.fcomb.services

import akka.actor._
import akka.persistence._
import akka.cluster.sharding._
import akka.stream.scaladsl._
import scala.concurrent.Future
import scala.collection.mutable.HashSet
// import scala.language.implicitConversions

object CertificateProcessor {
  case class GenerateUserCertificate(userId: Long)
}

class UserCertificateProcessor extends PersistentActor with ActorLogging {
  import context.dispatcher
  import CertificateProcessor._

  val persistenceId = "user-certificate-processor"

  @SerialVersionUID(1L)
  case class State(userIds: HashSet[Long])

  val state = State(HashSet())

  def receiveCommand = {
    case m @ GenerateUserCertificate(userId) =>
      state.userIds += userId
      persistAsync(m)(_ => ())
  }

  def receiveRecover = {
    case GenerateUserCertificate(userId) =>
      state.userIds += userId
    case SnapshotOffer(_, snapshot: State) =>
      state.userIds ++= snapshot.userIds
    case RecoveryCompleted =>
  }
}
