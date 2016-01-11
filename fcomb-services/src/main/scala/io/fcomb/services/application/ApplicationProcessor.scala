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
import scala.collection.immutable.LongMap
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
    askRef[Unit](appId, ApplicationScale(count), timeout)

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

  case class State(app: MApplication, containersMap: LongMap[MContainer]) {
    def containers() =
      containersMap.values.toList.sortBy(_.number)
  }

  case class Initialize(state: State)

  case object Annihilation

  case class Failed(e: Throwable)

  def receive = {
    case msg: Entity ⇒
      log.debug(s"msg: $msg")
      stash()
      initializing()
      context.become({
        case Initialize(state) ⇒
          context.become(initialized(state), false)
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
        val seq = containers.map(c ⇒ (c.getId, c))
        val state = State(app, LongMap(seq: _*))
        self ! Initialize(state)
      case Failure(e) ⇒ handleThrowable(e)
    }

  def initialized(state: State): Receive = {
    case msg: Entity ⇒ msg match {
      case ApplicationStart ⇒
        log.info(s"start application: ${state.app}")
        // if (state.app.state == ApplicationState.Created)
        //   UserNodesProcessor.createContainers(state.app)
        // else {
        //   Future.sequence(state.containersMap.values.map { c ⇒
        //     NodeProcessor.startContainer(c.nodeId.get, c.getId)
        //   }).map(_ ⇒ ()).pipeTo(sender())
        // }
        scale(state)
      case ApplicationStop ⇒
        log.info(s"stop application: ${state.app}")
        Future.sequence(state.containersMap.values.map { c ⇒
          NodeProcessor.stopContainer(c.nodeId.get, c.getId)
        }).map(_ ⇒ ()).pipeTo(sender())
      case ApplicationRedeploy ⇒
        log.info(s"redeploy application: ${state.app}")
        sender().!(())
      case ApplicationScale(count) ⇒
        log.info(s"scale application: ${state.app}")
        // TODO {

        val total = state.app.scaleStrategy.numberOfContainers - count
        PApplication.updateScaleStrategyNumberOfContainers(appId, total)
        val newState = state.copy(app = state.app.copy(
          scaleStrategy = state.app.scaleStrategy.copy(
            numberOfContainers = total
          )
        ))
        scale(newState).pipeTo(sender())
        context.become(initialized(newState), false)

      // }
      case ApplicationTerminate ⇒
        log.info(s"terminate application: ${state.app}")
        sender().!(())
      case ContainerChangedState(containerId, containerState) ⇒
        println(s"ContainerChangedState($containerId, $containerState)")
        PApplication.updateState(appId, ApplicationState.Running)
        val containerMap = state.containersMap.get(containerId) match {
          case Some(c) ⇒
            state.containersMap + ((containerId, c.copy(state = containerState)))
          case None ⇒ state.containersMap
        }
        context.become(initialized(state.copy(
          app = state.app.copy(state = ApplicationState.Running),
          containersMap = containerMap
        )), false)
      case NewContainer(container) ⇒
        println(s"NewContainer($container)")
        context.become(initialized(state.copy(
          containersMap = state.containersMap + ((container.getId, container))
        )), false)
      case WakeUp ⇒
        log.debug(s"awake application#$appId")
    }
    case ReceiveTimeout if (state.app.state == ApplicationState.Terminated) ⇒
      annihilation()
  }

  def scale(state: State) = {
    // default emptiest node strategy
    val ss = state.app.scaleStrategy
    val app = state.app
    val containers = state.containers().filter(_.isPresent())
    println(s"availableContainers: $containers, ${containers.length} < ${ss.numberOfContainers}")
    if (containers.length < ss.numberOfContainers) {
      val existsIds = containers.map(_.number)
      val newIds = (1 to ss.numberOfContainers).toList.diff(existsIds)
      for {
        containers ← PContainer.batchCreate(
          userId = app.userId,
          applicationId = app.getId,
          name = app.name,
          numbers = newIds
        )
        createdContainers ← Future.sequence(containers.map { c ⇒
          UserNodesProcessor.createContainer(c, app.image, app.deployOptions)
        })
        updatedContainers ← PContainer.batchPartialUpdate(createdContainers)
      } yield {
        println(s"updatedContainers: $updatedContainers")
        updatedContainers
      }
    }
    else if (containers.length > ss.numberOfContainers) {
      val terminateContainers = containers.drop(ss.numberOfContainers)
      println(s"terminateContainers: $terminateContainers")
      val ids = terminateContainers.map(_.getId)
      for {
        _ ← PContainer.updateState(ids, ContainerState.Terminating)
        _ ← Future.sequence(terminateContainers.map { c ⇒
          NodeProcessor.terminateContainer(c.nodeId.get, c.getId)
        })
        _ ← PContainer.updateState(ids, ContainerState.Terminated)
      } yield containers.take(ss.numberOfContainers)
    }
    else Future.successful(containers)
  }

  def start(state: State) = {
    val containers = state.containers().filter(_.isPresent())
      .filterNot { c ⇒
        c.state == ContainerState.Starting || c.state == ContainerState.Running
      }
    val ids = containers.map(_.getId)
    for {
      _ ← PContainer.updateState(ids, ContainerState.Starting)
      _ ← Future.sequence(containers.map { c ⇒
        NodeProcessor.startContainer(c.nodeId.get, c.getId)
      })
      _ ← PContainer.updateState(ids, ContainerState.Running)
    } yield containers.map(_.copy(state = ContainerState.Running))
  }

  def stop(state: State) = {
    val containers = state.containers().filter(_.isPresent())
      .filterNot { c ⇒
        c.state == ContainerState.Stopping || c.state == ContainerState.Stopped
      }
    val ids = containers.map(_.getId)
    for {
      _ ← PContainer.updateState(ids, ContainerState.Stopping)
      _ ← Future.sequence(containers.map { c ⇒
        NodeProcessor.stopContainer(c.nodeId.get, c.getId)
      })
      _ ← PContainer.updateState(ids, ContainerState.Stopped)
    } yield containers.map(_.copy(state = ContainerState.Stopped))
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
