package io.fcomb.services

import io.fcomb.models
import io.fcomb.utils.{Config, Implicits}
import io.fcomb.crypto.Certificate
import io.fcomb.persist.UserCertificate
import akka.actor._
import akka.cluster.sharding._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.Future
import scala.collection.mutable.HashSet
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import sun.security.x509.{X500Name, CertificateExtensions}
import sun.security.pkcs10.PKCS10
import java.security.cert.X509Certificate
import java.security.PrivateKey
import java.time.ZonedDateTime

// TODO: add router and distribution by userId

object CertificateProcessor {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(userId, payload) ⇒ (userId.toString, payload)
  }

  val numberOfShards = 1 // TODO: move into config

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(userId, _) ⇒ (userId % numberOfShards).toString
  }

  val shardName = "UserCertificateProcessor"

  def lookup()(implicit sys: ActorSystem) =
    sys.dispatchers.lookup(shardName)

  case class CertificateProcessorRegion(ref: ActorRef)

  def startRegion(timeout: Duration)(implicit sys: ActorSystem) =
    CertificateProcessorRegion(ClusterSharding(sys).start(
      typeName = shardName,
      entityProps = props(timeout),
      settings = ClusterShardingSettings(sys),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    ))

  sealed trait Entity

  case class SignRequest(
    request:          PKCS10,
    name:             X500Name,
    extOpt:           Option[CertificateExtensions] = None,
    expireAfterDays:  Int                           = Certificate.defaultExpireAfterDays
  ) extends Entity

  def props(timeout: Duration) =
    Props(classOf[UserCertificateProcessor], timeout)

  case class EntityEnvelope(userId: Long, payload: Entity)

  // def generateUserCertificates(userId: Long)(
  //   implicit
  //   sys: ActorSystem,
  //   timeout: Timeout = Timeout(30.seconds)
  // ): Future[(X509Certificate, PrivateKey)] =
  //   (lookup() ? GenerateUserCertificates(userId)).mapTo[(X509Certificate, PrivateKey)]
}

class UserCertificateProcessor(timeout: Duration) extends Actor with Stash with ActorLogging {
  import context.dispatcher
  import CertificateProcessor._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val userId = self.path.name.toLong

  case class Initialize(rootCert: models.UserCertificate)

  initializing()

  def receive = {
    case Initialize(rootCert) ⇒
      context.become(initialized(rootCert), false)
      unstashAll()
    case msg ⇒
      log.warning(s"stash message: $msg")
      stash()
  }

  def initializing(): Unit = {
    val replyTo = sender()
    UserCertificate.findRootCertByUserId(userId).onComplete {
      case Success(res) ⇒ res match {
        case Some(rootCert) ⇒ self ! Initialize(rootCert)
        case None           ⇒ generateAndPersistCertificates(replyTo)
      }
      case Failure(e) ⇒ handleThrowable(e, replyTo)
    }
  }

  def initialized(rootCert: models.UserCertificate): Receive = {
    case ReceiveTimeout ⇒ suicide()
    case m              ⇒ sender ! rootCert
  }

  def handleThrowable(e: Throwable, replyTo: ActorRef): Unit = {
    log.error(e, e.getMessage())
    replyTo ! Status.Failure(e)
    suicide()
  }

  def suicide() =
    context.parent ! Passivate(stopMessage = PoisonPill)

  def generateAndPersistCertificates(replyTo: ActorRef) = {
    val (rootCert, rootKey) =
      Certificate.generateRootAuthority(
        commonName = s"User#$userId",
        organizationalUnit = Config.certificateIssuer.organizationalUnit,
        organization = Config.certificateIssuer.organization,
        city = Config.certificateIssuer.city,
        state = Config.certificateIssuer.state,
        country = Config.certificateIssuer.country
      )
    val (clientCert, clientKey) =
      Certificate.generateClient(rootCert, rootKey)
    UserCertificate.createRootAndClient(
      userId = userId,
      rootCertificate = rootCert.getEncoded(),
      rootKey = rootKey.getEncoded(),
      clientCertificate = clientCert.getEncoded(),
      clientKey = clientKey.getEncoded()
    ).onComplete {
        case Success(res) ⇒
          res match {
            case scalaz.Success(rootCert) ⇒ self ! Initialize(rootCert)
            case scalaz.Failure(e)        ⇒ handleThrowable(e.head, replyTo)
          }
        case Failure(e) ⇒ handleThrowable(e, replyTo)
      }
  }
}
