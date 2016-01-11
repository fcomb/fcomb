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
import scala.collection.immutable.HashSet
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

  case class State(app: MApplication, containers: HashSet[MContainer])

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
        val state = State(app, HashSet(containers: _*))
        self ! Initialize(state)
      case Failure(e) ⇒ handleThrowable(e)
    }

  def initialized(state: State): Receive = {
    case msg: Entity ⇒ msg match {
      case ApplicationStart ⇒
        log.info(s"start application: ${state.app}")
        context.become({
          case Initialize(newState) ⇒
            log.info(s"newState: $newState")
            context.become(initialized(newState), false)
            unstashAll()
          case msg: Entity ⇒
            log.warning(s"stash message: $msg")
            stash()
        }, false)

        val replyTo = sender()
        scale(state).flatMap(start).foreach { s ⇒
          self ! Initialize(s)
          replyTo.!(())
        }
      case ApplicationStop ⇒
        log.info(s"stop application: ${state.app}")
        context.become({
          case Initialize(newState) ⇒
            context.become(initialized(newState), false)
            unstashAll()
          case msg: Entity ⇒
            log.warning(s"stash message: $msg")
            stash()
        }, false)

        val replyTo = sender()
        stop(state).foreach { s ⇒
          self ! Initialize(s)
          replyTo.!(())
        }
      case ApplicationRedeploy ⇒
        log.info(s"redeploy application: ${state.app}")
        sender().!(())
      case ApplicationScale(count) ⇒
        log.info(s"scale application: ${state.app}")
      // TODO {

      // val total = state.app.scaleStrategy.numberOfContainers - count
      // PApplication.updateScaleStrategyNumberOfContainers(appId, total)
      // val newState = state.copy(app = state.app.copy(
      //   scaleStrategy = state.app.scaleStrategy.copy(
      //     numberOfContainers = total
      //   )
      // ))
      // val replyTo = sender()
      // scale(newState).foreach { s ⇒
      //   context.become(initialized(s), false)
      //   replyTo.!(())
      // }

      // }
      case ApplicationTerminate ⇒
        log.info(s"terminate application: ${state.app}")
        sender().!(())
      case ContainerChangedState(containerId, containerState) ⇒
      // println(s"ContainerChangedState($containerId, $containerState)")
      // PApplication.updateState(appId, ApplicationState.Running)
      // val container = state.containers.find(_.getId == containerId) match {
      //   case Some(c) ⇒
      //     state.containers + c.copy(state = containerState)
      //   case None ⇒ state.containers
      // }
      // context.become(initialized(state.copy(
      //   app = state.app.copy(state = ApplicationState.Running),
      //   containers = container
      // )), false)
      case NewContainer(container)                            ⇒
      // println(s"NewContainer($container)")
      // context.become(initialized(state.copy(
      //   containers = state.containers + container
      // )), false)
      case WakeUp ⇒
        log.debug(s"awake application#$appId")
    }
    case ReceiveTimeout if (state.app.state == ApplicationState.Terminated) ⇒
      annihilation()
  }

  def scale(state: State) = {
    println(s"scale $state")
    // default emptiest node strategy
    val ss = state.app.scaleStrategy
    val app = state.app
    val containers = state.containers.toList
      .filter(_.isPresent())
      .sortBy(_.number)
    println(s"containers: ${containers}")
    println(s"availableContainers: $containers, ${containers.length} < ${ss.numberOfContainers}")
    if (containers.length < ss.numberOfContainers) {
      println("containers.length < ss.numberOfContainers")
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
        state.copy(
          containers = state.containers ++ updatedContainers
        )
      }
    }
    else if (containers.length > ss.numberOfContainers) {
      println("containers.length > ss.numberOfContainers")
      val terminateContainers = containers.drop(ss.numberOfContainers)
      println(s"terminateContainers: $terminateContainers")
      val ids = terminateContainers.map(_.getId)
      for {
        _ ← PContainer.updateState(ids, ContainerState.Terminating)
        _ ← Future.sequence(terminateContainers.map { c ⇒
          NodeProcessor.terminateContainer(c.nodeId.get, c.getId)
        })
        _ ← PContainer.updateState(ids, ContainerState.Terminated)
      } yield state.copy(
        containers = HashSet(containers.take(ss.numberOfContainers): _*)
      )
    }
    else {
      println(s"return same state: $state")
      Future.successful(state)
    }
  }

  def start(state: State) = {
    println(s"start $state")
    val containers = state.containers.filter { c ⇒
      c.isPresent() &&
        (c.state != ContainerState.Starting && c.state != ContainerState.Running)
    }
    if (containers.isEmpty) Future.successful(state)
    else {
      val ids = containers.map(_.getId)
      val idsSeq = ids.toSeq
      for {
        _ ← PApplication.updateState(appId, ApplicationState.Starting)
        _ ← PContainer.updateState(idsSeq, ContainerState.Starting)
        _ ← Future.sequence(containers.map { c ⇒
          NodeProcessor.startContainer(c.nodeId.get, c.getId)
        })
        _ ← PContainer.updateState(idsSeq, ContainerState.Running)
        _ ← PApplication.updateState(appId, ApplicationState.Running)
      } yield {
        val runningContainers = state.containers.map { c ⇒
          if (ids.contains(c.getId)) c.copy(state = ContainerState.Running)
          else c
        }
        state.copy(
          app = state.app.copy(state = ApplicationState.Running),
          containers = runningContainers
        )
      }
    }
  }

  def stop(state: State) = {
    val containers = state.containers.filter { c ⇒
      c.isPresent() &&
        (c.state != ContainerState.Stopping && c.state != ContainerState.Stopped)
    }
    if (containers.isEmpty) Future.successful(state)
    else {
      val ids = containers.map(_.getId)
      val idsSeq = ids.toSeq
      for {
        _ ← PApplication.updateState(appId, ApplicationState.Stopping)
        _ ← PContainer.updateState(idsSeq, ContainerState.Stopping)
        _ ← Future.sequence(containers.map { c ⇒
          NodeProcessor.stopContainer(c.nodeId.get, c.getId)
        })
        _ ← PContainer.updateState(idsSeq, ContainerState.Stopped)
        _ ← PApplication.updateState(appId, ApplicationState.Stopped)
      } yield {
        val stoppedContainers = state.containers.map { c ⇒
          if (ids.contains(c.getId)) c.copy(state = ContainerState.Stopped)
          else c
        }
        state.copy(
          app = state.app.copy(state = ApplicationState.Stopped),
          containers = stoppedContainers
        )
      }
    }
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
