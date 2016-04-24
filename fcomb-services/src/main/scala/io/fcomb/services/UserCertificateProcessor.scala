package io.fcomb.services

import io.fcomb.services.Exceptions._
import io.fcomb.models
import io.fcomb.utils.{Config, Implicits}
import io.fcomb.crypto.Certificate
import io.fcomb.persist.UserCertificate
import akka.actor._
import akka.cluster.sharding._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Promise}
import scala.collection.mutable.HashSet
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import java.security.{KeyFactory, PrivateKey}
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.time.ZonedDateTime
import sun.security.pkcs10.PKCS10
import sun.security.x509.{CertificateExtensions, X500Name, X509CertImpl}
import cats.data.Validated

// TODO: add router and distribution by userId

object UserCertificateProcessor {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(userId, payload) ⇒ (userId.toString, payload)
  }

  val numberOfShards = 1 // TODO: move into config

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(userId, _) ⇒ (userId % numberOfShards).toString
  }

  val shardName = "user-certificate-processor"

  def startRegion(timeout: Duration)(implicit sys: ActorSystem) = {
    val ref = ClusterSharding(sys).start(
      typeName = shardName,
      entityProps = props(timeout),
      settings = ClusterShardingSettings(sys),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
    actorRef = ref
    ref
  }

  sealed trait Entity

  case class SignRequest(
    request:         PKCS10,
    name:            X500Name,
    extOpt:          Option[CertificateExtensions],
    expireAfterDays: Int
  ) extends Entity

  def props(timeout: Duration) =
    Props(classOf[UserCertificateProcessor], timeout)

  case class EntityEnvelope(userId: Long, payload: Entity)

  private var actorRef: ActorRef = _

  case class SignedCertificate(
    certificate:   X509Certificate,
    certificateId: Long
  )

  def generateUserCertificates(
    userId:          Long,
    request:         PKCS10,
    name:            X500Name,
    extOpt:          Option[CertificateExtensions] = None,
    expireAfterDays: Int                           = Certificate.defaultExpireAfterDays
  )(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[SignedCertificate] = {
    Option(actorRef) match {
      case Some(ref) ⇒
        val req = SignRequest(request, name, extOpt, expireAfterDays)
        ask(ref, EntityEnvelope(userId, req)).mapTo[SignedCertificate]
      case None ⇒ Future.failed(EmptyActorRefException)
    }
  }
}

class UserCertificateProcessor(timeout: Duration) extends Actor with Stash with ActorLogging {
  import context.dispatcher
  import UserCertificateProcessor._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val userId = self.path.name.toLong

  case class Initialize(cert: X509Certificate, key: PrivateKey, certificateId: Long)

  case object Annihilation

  case class Failed(e: Throwable)

  initializing()

  def receive = {
    case Initialize(cert, key, certificateId) ⇒
      context.become(initialized(cert, key, certificateId), false)
      unstashAll()
    case msg: Entity ⇒
      log.warning(s"stash message: $msg")
      stash()
  }

  def initializing(): Unit = {
    UserCertificate.findRootCertByUserId(userId).onComplete {
      case Success(res) ⇒ res match {
        case Some(cert) ⇒ self ! initializeWith(cert)
        case None       ⇒ generateAndPersistCertificates()
      }
      case Failure(e) ⇒ handleThrowable(e)
    }
  }

  def initialized(cert: X509Certificate, key: PrivateKey, certificateId: Long): Receive = {
    case SignRequest(req, name, extOpt, expireAfterDays) ⇒
      val signed = Certificate.signCertificationRequest(cert, key,
        req, name, extOpt, expireAfterDays)
      sender ! SignedCertificate(signed, certificateId)
    case ReceiveTimeout ⇒ annihilation()
  }

  def failed(e: Throwable): Receive = {
    case _: Entity    ⇒ sender ! Status.Failure(e)
    case Annihilation ⇒ annihilation()
  }

  def handleThrowable(e: Throwable): Unit = {
    log.error(e, e.getMessage())
    context.become({
      case Failed(e) ⇒
        context.become(failed(e), false)
        unstashAll()
        self ! Annihilation
    }, false)
    self ! Failed(e)
  }

  def initializeWith(rootCert: models.UserCertificate) = {
    val cert = new X509CertImpl(rootCert.certificate)
    val kf = KeyFactory.getInstance("RSA")
    val spec = new PKCS8EncodedKeySpec(rootCert.key)
    val key = kf.generatePrivate(spec)
    Initialize(cert, key, rootCert.getId)
  }

  def annihilation() = {
    log.info("annihilation!")
    context.parent ! Passivate(stopMessage = PoisonPill)
  }

  def generateAndPersistCertificates() = {
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
            case Validated.Valid(cert) ⇒ self ! initializeWith(cert)
            case Validated.Invalid(e)  ⇒ handleThrowable(e.head)
          }
        case Failure(e) ⇒ handleThrowable(e)
      }
  }
}
