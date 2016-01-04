package io.fcomb.services.application

import io.fcomb.services.Exceptions._
import io.fcomb.services.node.UserNodesProcessor
import io.fcomb.models.application.{ApplicationState, Application ⇒ MApplication}
import io.fcomb.models.docker.ContainerState
import io.fcomb.utils.Config
import io.fcomb.persist.application.{Application ⇒ PApplication}
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

object ApplicationProcessor {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(appId, payload) ⇒ (appId.toString, payload)
  }

  val numberOfShards = 1

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(appId, _) ⇒ (appId % numberOfShards).toString
  }

  val shardName = "ApplicationProcessor"

  def startRegion(timeout: Duration)(
    implicit
    sys: ActorSystem
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

  case object ApplicationStart extends Entity

  case class NewContainerState(
    id:    Long,
    state: ContainerState.ContainerState
  ) extends Entity

  def props(timeout: Duration) =
    Props(new ApplicationProcessor(timeout))

  case class EntityEnvelope(appId: Long, payload: Entity)

  private var actorRef: ActorRef = _

  def start(appId: Long)(
    implicit
    timeout: Timeout = Timeout(5.minutes)
  ): Future[Unit] = {
    Option(actorRef) match {
      case Some(ref) ⇒
        ask(ref, EntityEnvelope(appId, ApplicationStart))
          .mapTo[Unit]
      case None ⇒ Future.failed(EmptyActorRefException)
    }
  }

  def newContainerState(
    appId:       Long,
    containerId: Long,
    state:       ContainerState.ContainerState
  ) = {
    Option(actorRef) match {
      case Some(ref) ⇒
        ref ! EntityEnvelope(appId, NewContainerState(containerId, state))
      case None ⇒ Future.failed(EmptyActorRefException)
    }
  }
}

private[this] object ApplicationProcessorMessages {
  sealed trait ApplicationCommands
}

class ApplicationProcessor(timeout: Duration) extends Actor
    with Stash with ActorLogging {
  import context.dispatcher
  import context.system
  import ApplicationProcessor._
  import ApplicationProcessorMessages._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val appId = self.path.name.toLong

  case class Initialize(app: MApplication)

  case object Stop

  case class Failed(e: Throwable)

  def receive = {
    case msg: Entity ⇒
      log.debug(s"msg: $msg")
      stash()
      initializing()
      context.become({
        case Initialize(app) ⇒
          context.become(initialized(app), false)
          unstashAll()
        case msg: Entity ⇒
          log.warning(s"stash message: $msg")
          stash()
      }, false)
  }

  def initializing() =
    (for {
      // TODO: add OptionT
      Some(app) ← PApplication.findByPk(appId)
    } yield app).onComplete {
      case Success(app) ⇒ self ! Initialize(app)
      case Failure(e)   ⇒ handleThrowable(e)
    }

  def initialized(app: MApplication): Receive = {
    case msg: Entity ⇒ msg match {
      case ApplicationStart ⇒
        log.info(s"start application: $app")
        UserNodesProcessor.createContainers(app)
      case NewContainerState(containerId, containerState) ⇒
        println(s"NewContainerState($containerId, $containerState)")
        PApplication.updateState(appId, ApplicationState.Running)
    }
    // TODO: case ReceiveTimeout ⇒ annihilation()
  }

  def failed(e: Throwable): Receive = {
    case _: Entity ⇒ sender ! Status.Failure(e)
    case Stop      ⇒ annihilation()
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

  def annihilation() = {
    log.info("annihilation!")
    context.parent ! Passivate(stopMessage = PoisonPill)
  }
}
