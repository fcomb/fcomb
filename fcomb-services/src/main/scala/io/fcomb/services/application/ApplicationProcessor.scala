package io.fcomb.services.application

import io.fcomb.services.Exceptions._
import io.fcomb.services.node.{NodeProcessor, UserNodesProcessor}
import io.fcomb.models.application.{ApplicationState, Application ⇒ MApplication}
import io.fcomb.models.docker.{ContainerState, Container ⇒ MContainer}
import io.fcomb.utils.Config
import io.fcomb.persist.application.{Application ⇒ PApplication}
import io.fcomb.persist.docker.{Container ⇒ PContainer}
import akka.actor._
import akka.stream.Materializer
import akka.cluster.sharding._
import akka.pattern.{ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.collection.mutable.{HashSet, LongMap}
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

  def initialize()(
    implicit
    ec: ExecutionContext
  ) =
    // TODO: only with avaialable state
    PApplication.all().map(_.foreach { app ⇒
      actorRef ! EntityEnvelope(app.getId, WakeUp)
    })

  sealed trait Entity

  case object WakeUp extends Entity

  case object ApplicationStart extends Entity

  case object ApplicationStop extends Entity

  case object ApplicationRedeploy extends Entity

  case class ApplicationScale(count: Int) extends Entity

  case object ApplicationTerminate extends Entity

  case class ContainerChangedState(
    id:    Long,
    state: ContainerState.ContainerState
  ) extends Entity

  case class NewContainer(container: MContainer) extends Entity

  def props(timeout: Duration) =
    Props(new ApplicationProcessor(timeout))

  case class EntityEnvelope(appId: Long, payload: Entity)

  private var actorRef: ActorRef = _

  private def askRef[T](
    appId:   Long,
    entity:  Entity,
    timeout: Timeout
  )(
    implicit
    m: Manifest[T]
  ): Future[T] =
    Option(actorRef) match {
      case Some(ref) ⇒
        ask(ref, EntityEnvelope(appId, entity))(timeout).mapTo[T]
      case None ⇒ Future.failed(EmptyActorRefException)
    }

  private def tellRef(appId: Long, entity: Entity) =
    Option(actorRef).map(_ ! EntityEnvelope(appId, entity))

  def start(appId: Long)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationStart, timeout)

  def stop(appId: Long)(
    implicit
    timeout: Timeout = Timeout(1.minute)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationStop, timeout)

  def terminate(appId: Long)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationTerminate, timeout)

  def redeploy(appId: Long)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationRedeploy, timeout)

  def scale(appId: Long, count: Int)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef(appId, ApplicationScale(count), timeout)

  def containerChangedState(container: MContainer) =
    tellRef(
      container.applicationId,
      ContainerChangedState(container.getId, container.state)
    )

  def newContainer(container: MContainer) =
    tellRef(container.applicationId, NewContainer(container))
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

  private val containersMap = new LongMap[MContainer]()

  case class Initialize(app: MApplication, containers: Seq[MContainer])

  case object Annihilation

  case class Failed(e: Throwable)

  def receive = {
    case msg: Entity ⇒
      log.debug(s"msg: $msg")
      stash()
      initializing()
      context.become({
        case Initialize(app, containers) ⇒
          containersMap ++= containers.map(c ⇒ (c.getId, c))
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
      containers ← PContainer.findAllByApplicationId(appId)
    } yield (app, containers)).onComplete {
      case Success((app, containers)) ⇒
        self ! Initialize(app, containers)
      case Failure(e) ⇒ handleThrowable(e)
    }

  def initialized(app: MApplication): Receive = {
    case msg: Entity ⇒ msg match {
      case ApplicationStart ⇒
        log.info(s"start application: $app")
        // TODO: move into FSM receive
        if (app.state == ApplicationState.Created)
          UserNodesProcessor.createContainers(app)
        else {
          Future.sequence(containersMap.values.map { c ⇒
            NodeProcessor.startContainer(c.nodeId, c.getId)
          }).map(_ ⇒ ()).pipeTo(sender())
        }
      case ApplicationStop ⇒
        log.info(s"stop application: $app")
        Future.sequence(containersMap.values.map { c ⇒
          NodeProcessor.stopContainer(c.nodeId, c.getId)
        }).map(_ ⇒ ()).pipeTo(sender())
      case ApplicationRedeploy ⇒
        log.info(s"redeploy application: $app")
        sender().!(())
      case ApplicationScale(count) ⇒
        log.info(s"scale application: $app")
        sender().!(())
      case ApplicationTerminate ⇒
        log.info(s"terminate application: $app")
        sender().!(())
      case ContainerChangedState(containerId, containerState) ⇒
        println(s"ContainerChangedState($containerId, $containerState)")
        val napp = app.copy(state = ApplicationState.Running)
        PApplication.updateState(appId, ApplicationState.Running)
        containersMap.get(containerId).foreach { c ⇒
          containersMap += (containerId, c.copy(state = containerState))
        }
        context.become(initialized(napp), false)
      case NewContainer(container) =>
        println(s"NewContainer($container)")
        containersMap += (container.getId, container)
      case WakeUp ⇒
        log.debug(s"awake application#$appId")
    }
    // TODO: case ReceiveTimeout ⇒ annihilation()
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
