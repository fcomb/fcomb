package io.fcomb.services.node

import io.fcomb.services.Exceptions._
import io.fcomb.services.UserCertificateProcessor
import io.fcomb.models.node.{Node ⇒ MNode}
import io.fcomb.utils.{Config, Implicits}
import io.fcomb.crypto.Certificate
import io.fcomb.persist.node.{Node ⇒ PNode}
import akka.actor._
import akka.cluster.sharding._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Promise}
import scala.collection.mutable.HashSet
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import sun.security.x509.{X500Name, CertificateExtensions}
import sun.security.pkcs10.PKCS10
import java.security.cert.X509Certificate
import java.security.PrivateKey
import java.time.ZonedDateTime

object NodeJoinProcessor {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(hash, payload) ⇒ (hash, payload)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(_, _) ⇒ "0"
  }

  val shardName = "NodeJoinProcessor"

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

  case class JoinNode(userId: Long, request: PKCS10) extends Entity

  def props(timeout: Duration) =
    Props(classOf[NodeJoinProcessor], timeout)

  case class EntityEnvelope(hash: String, payload: Entity)

  private var actorRef: ActorRef = _

  def join(userId: Long, req: PKCS10)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[MNode] = {
    Option(actorRef) match {
      case Some(ref) ⇒
        val hash = PNode.getPublicKeyHash(req.getSubjectPublicKeyInfo())
        ask(ref, EntityEnvelope(hash, JoinNode(userId, req)))
          .mapTo[MNode]
      case None ⇒ Future.failed(EmptyActorRefException)
    }
  }
}

class NodeJoinProcessor(timeout: Duration) extends Actor with Stash with ActorLogging {
  import context.dispatcher
  import NodeJoinProcessor._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val publicKeyHash = self.path.name

  case class Initialize(node: MNode)

  case object Stop

  case class Failed(e: Throwable)

  def receive = {
    case JoinNode(userId, req) ⇒
      stash()
      initializing(userId, req)
      context.become({
        case Initialize(node) ⇒
          context.become(initialized(node), false)
          unstashAll()
        case msg: Entity ⇒
          log.warning(s"stash message: $msg")
          stash()
      }, false)
  }

  def initializing(userId: Long, req: PKCS10) =
    PNode.findByPublicKeyHash(publicKeyHash).onComplete {
      case Success(res) ⇒ res match {
        case Some(node) ⇒ self ! Initialize(node)
        case None       ⇒ joinNode(userId, req)
      }
      case Failure(e) ⇒ handleThrowable(e)
    }

  def initialized(node: MNode): Receive = {
    case _: JoinNode    ⇒ sender ! node
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

  def suicide() = {
    log.info("suicide!")
    context.parent ! Passivate(stopMessage = PoisonPill)
  }

  def joinNode(userId: Long, req: PKCS10) =
    (for {
      nodeId ← PNode.getNodeIdSequence()
      name = new X500Name(s"CN=node-$nodeId")
      signed ← UserCertificateProcessor
        .generateUserCertificates(userId, req, name)
      res ← PNode.create(
        nodeId,
        userId,
        signed.certificateId,
        signed.certificate.getEncoded(),
        publicKeyHash
      )
    } yield res match {
      case scalaz.Success(node) ⇒
        self ! Initialize(node)
      case scalaz.Failure(e) ⇒
        throw e.head
    }).recover {
      case e: Throwable ⇒ handleThrowable(e)
    }
}
