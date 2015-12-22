package io.fcomb.services

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

// TODO: add router and distribution by userId

case object EmptyActorRef extends Throwable

object CertificateProcessor {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(userId, payload) ⇒ (userId.toString, payload)
  }

  val numberOfShards = 1 // TODO: move into config

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(userId, _) ⇒ (userId % numberOfShards).toString
  }

  val shardName = "UserCertificateProcessor"

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

  def generateUserCertificates(
    userId:          Long,
    request:         PKCS10,
    name:            X500Name,
    extOpt:          Option[CertificateExtensions] = None,
    expireAfterDays: Int                           = Certificate.defaultExpireAfterDays
  )(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[X509Certificate] = {
    Option(actorRef) match {
      case Some(ref) ⇒
        val req = SignRequest(request, name, extOpt, expireAfterDays)
        ask(ref, EntityEnvelope(userId, req)).mapTo[X509Certificate]
      case None ⇒ Future.failed(EmptyActorRef)
    }
  }
}

class UserCertificateProcessor(timeout: Duration) extends Actor with Stash with ActorLogging {
  import context.dispatcher
  import CertificateProcessor._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val userId = self.path.name.toLong

  case class Initialize(cert: X509Certificate, key: PrivateKey)

  case object Stop

  case class Failed(e: Throwable)

  initializing()

  def receive = {
    case Initialize(cert, key) ⇒
      context.become(initialized(cert, key), false)
      unstashAll()
    case msg ⇒
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

  def initialized(cert: X509Certificate, key: PrivateKey): Receive = {
    case SignRequest(req, name, extOpt, expireAfterDays) ⇒
      val signed = Certificate.signCertificationRequest(cert, key,
        req, name, extOpt, expireAfterDays)
      sender ! signed
    case ReceiveTimeout ⇒ suicide()
  }

  def failed(e: Throwable): Receive = {
    case _: Entity ⇒ sender ! Status.Failure(e)
    case Stop      ⇒ suicide()
  }

  def handleThrowable(e: Throwable): Unit = {
    log.error(e, e.getMessage())
    context.become({
      case Failed(e) ⇒
        context.become(failed(e), false)
        unstashAll()
        self ! Stop
    }, false)
    self ! Failed(e)
  }

  def initializeWith(rootCert: models.UserCertificate) = {
    val cert = new X509CertImpl(rootCert.certificate)
    val kf = KeyFactory.getInstance("RSA")
    val spec = new PKCS8EncodedKeySpec(rootCert.key)
    val key = kf.generatePrivate(spec)
    Initialize(cert, key)
  }

  def suicide() =
    context.parent ! Passivate(stopMessage = PoisonPill)

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
            case scalaz.Success(cert) ⇒ self ! initializeWith(cert)
            case scalaz.Failure(e)    ⇒ handleThrowable(e.head)
          }
        case Failure(e) ⇒ handleThrowable(e)
      }
  }
}
