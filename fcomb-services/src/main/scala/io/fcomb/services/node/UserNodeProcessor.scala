package io.fcomb.services.node

import io.fcomb.services.Exceptions._
import io.fcomb.services._
import io.fcomb.models.application.{DockerImage, DockerDeployOptions, ScaleStrategy, Application ⇒ MApplication}
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

sealed trait ReserveResult

object ReserveResult {
  case class NodeReserve(id: Long, numberOfContainers: Int)

  case class Reserved(nodes: List[NodeReserve]) extends ReserveResult

  case object NoNodesAvailable extends ReserveResult
}

object UserNodeProcessor extends Processor[Long] {
  val numberOfShards = 1

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) ⇒ (id % numberOfShards).toString
  }

  val shardName = "user-node-processor"

  def startRegion(timeout: Duration)(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) =
    startClustedSharding(props(timeout))

  case class Reserve(scaleStrategy: ScaleStrategy) extends Entity

  def props(timeout: Duration)(implicit mat: Materializer) =
    Props(new UserNodeProcessor(timeout))

  def reserve(userId: Long, scaleStrategy: ScaleStrategy)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[ReserveResult] =
    askRef[ReserveResult](userId, Reserve(scaleStrategy), timeout)
}

object UserNodeMessages {
  case class State(nodes: HashSet[MNode])
}

class UserNodeProcessor(timeout: Duration)(implicit mat: Materializer) extends Actor
    with Stash with ActorLogging with ProcessorActor[UserNodeMessages.State] {
  import context.dispatcher
  import context.system
  import UserNodeProcessor._
  import UserNodeMessages._

  context.setReceiveTimeout(timeout)

  val userId = self.path.name.toLong

  def initializing() = {
    PNode.findAllAvailableByUserId(userId).onComplete {
      case Success(nodes) ⇒ initialize(State(HashSet(nodes: _*)))
      case Failure(e)     ⇒ handleThrowable(e)
    }
  }

  def stateReceive(state: State): Receive = {
    case msg: Entity ⇒ msg match {
      case _ ⇒
        sender() ! ReserveResult.NoNodesAvailable
      // case ContainerCreate(container, image, deployOptions) ⇒
      //   // TODO: ask nodes for ability to run this container
      //   println(s"nodes: $nodes")
      //   nodes.headOption match {
      //     case Some(node) ⇒
      //       println(s"NodeProcessor.createContainer: ${node.getId}")
      //       NodeProcessor.containerCreate(node.getId, container, image, deployOptions)
      //         .pipeTo(sender())
      //     case None ⇒
      //       val e = new Throwable("No available nodes for running this container")
      //       log.error(e.getMessage)
      //       sender() ! Status.Failure(e)
      //   }
    }
    case ReceiveTimeout ⇒
      if (state.nodes.isEmpty) annihilation()
  }
}
