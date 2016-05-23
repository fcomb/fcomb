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
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.collection.immutable
import scala.concurrent.duration._
import scala.util.{Success, Failure, Random}
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
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(30.seconds)
  ): Future[ReserveResult] =
    askRef[ReserveResult](userId, Reserve(scaleStrategy), timeout)
}

object UserNodeMessages {
  case class State()
}

class UserNodeProcessor(timeout: Duration)(implicit mat: Materializer)
    extends Actor
    with Stash
    with ActorLogging
    with ProcessorActor[UserNodeMessages.State] {
  import context.dispatcher
  import context.system
  import UserNodeProcessor._
  import UserNodeMessages._

  context.setReceiveTimeout(timeout)

  val userId = self.path.name.toLong

  lazy val rand = new Random()

  private[this] var nodes: immutable.HashSet[MNode] = immutable.HashSet.empty

  protected def getInitialState = State()

  def initializing() =
    putStateAsync {
      PNode.findAllAvailableByUserId(userId).map { nodes ⇒
        this.nodes = immutable.HashSet(nodes: _*)
        State()
      }
    }

  initializing()

  def receive: Receive = {
    case msg: Entity ⇒
      msg match {
        case Reserve(scaleStrategy) ⇒
          if (this.nodes.isEmpty)
            sender() ! ReserveResult.NoNodesAvailable
          else {
            val idx = rand.nextInt(this.nodes.size)
            val node = this.nodes.iterator.drop(idx).next
            val reserved = ReserveResult.NodeReserve(
              node.getId,
              scaleStrategy.numberOfContainers
            )
            sender() ! ReserveResult.Reserved(List(reserved))
          }
      }
    case ReceiveTimeout ⇒
      if (this.nodes.isEmpty) annihilation()
  }
}
