package io.fcomb.services.node

import io.fcomb.services.Exceptions._
import io.fcomb.services.UserCertificateProcessor
import io.fcomb.models.application.{Application ⇒ MApplication}
import io.fcomb.persist.docker.{Container ⇒ PContainer}
import io.fcomb.models.node.{NodeState, Node ⇒ MNode}
import io.fcomb.utils.{Config, Implicits, Random}
import io.fcomb.crypto.{Certificate, Tls}
import io.fcomb.persist.node.{Node ⇒ PNode}
import io.fcomb.persist.UserCertificate
import akka.actor._
import akka.stream.Materializer
import akka.cluster.sharding._
import akka.pattern.{after, ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Promise}
import scala.collection.mutable.HashSet
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import java.time.ZonedDateTime
import java.net.InetAddress

object UserNodesProcessor {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(userId, payload) ⇒ (userId.toString, payload)
  }

  val numberOfShards = 1

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(userId, _) ⇒ (userId % numberOfShards).toString
  }

  val shardName = "UserNodesProcessor"

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

  case class CreateContainer(app: MApplication) extends Entity

  def props(timeout: Duration)(implicit mat: Materializer) =
    Props(new UserNodesProcessor(timeout))

  case class EntityEnvelope(nodeId: Long, payload: Entity)

  private var actorRef: ActorRef = _

  def createContainers(app: MApplication)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[PContainer.ValidationModel] = {
    // TODO: DRY
    Option(actorRef) match {
      case Some(ref) ⇒
        ask(ref, EntityEnvelope(app.userId, CreateContainer(app)))
          .mapTo[PContainer.ValidationModel]
      case None ⇒ Future.failed(EmptyActorRefException)
    }
  }
}

private[this] object UserNodesMessages {
}

class UserNodesProcessor(timeout: Duration)(implicit mat: Materializer) extends Actor
    with Stash with ActorLogging {
  import context.dispatcher
  import context.system
  import UserNodesProcessor._
  import UserNodesMessages._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val userId = self.path.name.toLong

  case class Initialize(nodes: Seq[MNode])

  case object Annihilation

  case class Failed(e: Throwable)

  def receive = {
    case msg: Entity ⇒
      stash()
      context.become({
        case Initialize(nodes) ⇒
          context.become(initialized(nodes), false)
          unstashAll()
        case msg: Entity ⇒
          log.warning(s"stash message: $msg")
          stash()
      }, false)
      initializing()
  }

  def initializing() = {
    PNode.findAllAvailableByUserId(userId).onComplete {
      case Success(nodes) ⇒
        self ! Initialize(nodes)
      case Failure(e) ⇒ handleThrowable(e)
    }
  }

  def initialized(nodes: Seq[MNode]): Receive = {
    case msg: Entity ⇒ msg match {
      case CreateContainer(app) ⇒
        // TODO: ask nodes for ability to run this container
        nodes.headOption match {
          case Some(node) ⇒
            NodeProcessor.createContainer(node.getId, app)
              .pipeTo(sender())
          case None ⇒
            val e = new Throwable("No available nodes for running this container")
            log.error(e.getMessage)
            sender() ! Status.Failure(e)
        }
    }
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

  def annihilation() = {
    log.info("annihilation!")
    context.parent ! Passivate(stopMessage = PoisonPill)
  }
}
