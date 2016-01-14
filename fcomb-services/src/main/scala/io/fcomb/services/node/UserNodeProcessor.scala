package io.fcomb.services.node

import io.fcomb.services.Exceptions._
import io.fcomb.services.UserCertificateProcessor
import io.fcomb.models.application.{DockerImage, DockerDeployOptions, Application ⇒ MApplication}
import io.fcomb.models.docker.{Container ⇒ MContainer}
import io.fcomb.models.node.{NodeState, Node ⇒ MNode}
import io.fcomb.persist.docker.{Container ⇒ PContainer}
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

object UserNodeProcessor {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(userId, payload) ⇒ (userId.toString, payload)
  }

  val numberOfShards = 1

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(userId, _) ⇒ (userId % numberOfShards).toString
  }

  val shardName = "user-node-processor"

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

  case class ContainerCreate(
    container:     MContainer,
    image:         DockerImage,
    deployOptions: DockerDeployOptions
  ) extends Entity

  def props(timeout: Duration)(implicit mat: Materializer) =
    Props(new UserNodeProcessor(timeout))

  case class EntityEnvelope(nodeId: Long, payload: Entity)

  private var actorRef: ActorRef = _

  def containerCreate(
    container:     MContainer,
    image:         DockerImage,
    deployOptions: DockerDeployOptions
  )(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[MContainer] = {
    // TODO: DRY
    Option(actorRef) match {
      case Some(ref) ⇒
        val entity = EntityEnvelope(
          container.userId,
          ContainerCreate(container, image, deployOptions)
        )
        ask(ref, entity).mapTo[MContainer]
      case None ⇒ Future.failed(EmptyActorRefException)
    }
  }
}

private[this] object UserNodeMessages {
}

class UserNodeProcessor(timeout: Duration)(implicit mat: Materializer) extends Actor
    with Stash with ActorLogging {
  import context.dispatcher
  import context.system
  import UserNodeProcessor._
  import UserNodeMessages._
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
      case ContainerCreate(container, image, deployOptions) ⇒
        // TODO: ask nodes for ability to run this container
        println(s"nodes: $nodes")
        nodes.headOption match {
          case Some(node) ⇒
            println(s"NodeProcessor.createContainer: ${node.getId}")
            NodeProcessor.containerCreate(node.getId, container, image, deployOptions)
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
