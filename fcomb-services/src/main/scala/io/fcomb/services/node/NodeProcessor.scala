package io.fcomb.services.node

import io.fcomb.services.Exceptions._
import io.fcomb.services.UserCertificateProcessor
import io.fcomb.models.node.{Node ⇒ MNode}
import io.fcomb.utils.{Config, Implicits, Random}
import io.fcomb.crypto.{Certificate, Tls}
import io.fcomb.persist.node.{Node ⇒ PNode}
import io.fcomb.persist.UserCertificate
import io.fcomb.docker.api.Client
import akka.actor._
import akka.stream.Materializer
import akka.cluster.sharding._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Promise}
import scala.collection.mutable.HashSet
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import java.time.ZonedDateTime
import java.net.InetAddress

object NodeProcessor {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(nodeId, payload) ⇒ (nodeId.toString, payload)
  }

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(nodeId, _) ⇒ nodeId.toString
  }

  val shardName = "NodeProcessor"

  def startRegion(timeout: Duration)(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) = {
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

  case class RegisterNode(ip: InetAddress) extends Entity

  def props(timeout: Duration)(implicit mat: Materializer) =
    Props(new NodeProcessor(timeout))

  case class EntityEnvelope(nodeId: Long, payload: Entity)

  private var actorRef: ActorRef = _

  def register(nodeId: Long, ip: InetAddress)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] = {
    Option(actorRef) match {
      case Some(ref) ⇒
        ask(ref, EntityEnvelope(nodeId, RegisterNode(ip)))
          .mapTo[Unit]
      case None ⇒ Future.failed(EmptyActorRefException)
    }
  }
}

private[this] object NodeProcessorMessages {
  sealed trait DockerApiCommands

  case object DockerPing extends DockerApiCommands
}

class NodeProcessor(timeout: Duration)(implicit mat: Materializer) extends Actor
    with Stash with ActorLogging {
  import context.dispatcher
  import context.system
  import NodeProcessor._
  import NodeProcessorMessages._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val nodeId = self.path.name.toLong
  // TODO: val dockerApiTimeout = 1.minute
  val pingInterval = (25 + Random.random.nextInt(15)).seconds

  case class DockerApiCerts(key: Array[Byte], cert: Array[Byte], ca: Array[Byte])

  case class Initialize(node: MNode, certs: DockerApiCerts)

  case object Stop

  case class Failed(e: Throwable)

  system.scheduler.schedule(pingInterval, pingInterval)(self ! DockerPing)

  def receive = {
    case RegisterNode(ip) ⇒
      stash()
      initializing(ip)
      context.become({
        case Initialize(node, certs) ⇒
          val apiClient = createApiClient(node, certs)
          context.become(initialized(apiClient, node, certs), false)
          unstashAll()
        case msg: Entity ⇒
          log.warning(s"stash message: $msg")
          stash()
      }, false)
  }

  def initializing(ip: InetAddress) =
    (for {
      // TODO: add OptionT
      Some(node) ← PNode.findByPk(nodeId)
      Some((rootCert, clientCert)) ← UserCertificate
        .findRootAndClientCertsByUserId(node.userId)
    } yield (node, rootCert, clientCert)).onComplete {
      case Success((node, rootCert, clientCert)) ⇒
        val certs = DockerApiCerts(
          clientCert.key,
          clientCert.certificate,
          rootCert.certificate
        )
        if (node.publicIpInetAddress().exists(_ == ip))
          self ! Initialize(node, certs)
        else {
          val ipAddress = ip.getHostAddress
          PNode.updatePublicIpAddress(nodeId, ipAddress).onComplete {
            case Success(_) ⇒
              val nn = node.copy(publicIpAddress = Some(ipAddress))
              self ! Initialize(nn, certs)
            case Failure(e) ⇒ handleThrowable(e)
          }
        }
      case Failure(e) ⇒ handleThrowable(e)
    }

  def initialized(
    apiClient: Client,
    node:      MNode,
    certs:     DockerApiCerts
  ): Receive = {
    case RegisterNode(ip) ⇒
      if (node.publicIpInetAddress().exists(_ == ip))
        apiClient.ping.pipeTo(sender())
      else ??? // TODO: stash all messages and update IP address
    case cmd: DockerApiCommands ⇒ cmd match {
      case DockerPing ⇒
        apiClient.ping().onComplete(println)
    }
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

  def createApiClient(node: MNode, certs: DockerApiCerts) = {
    val sslContext = Tls.context(certs.key, certs.cert, Some(certs.ca))
    new Client(node.publicIpAddress.get, 2375, Some(sslContext))
  }

  // def joinNode(userId: Long, req: PKCS10) =
  //   (for {
  //     nodeId ← PNode.getNodeIdSequence()
  //     name = new X500Name(s"CN=node-$nodeId")
  //     signed ← UserCertificateProcessor
  //       .generateUserCertificates(userId, req, name)
  //     res ← PNode.create(
  //       nodeId,
  //       userId,
  //       signed.certificateId,
  //       signed.certificate.getEncoded(),
  //       publicKeyHash
  //     )
  //   } yield res match {
  //     case scalaz.Success(node) ⇒
  //       self ! Initialize(node)
  //     case scalaz.Failure(e) ⇒
  //       throw e.head
  //   }).recover {
  //     case e: Throwable ⇒ handleThrowable(e)
  //   }
}
