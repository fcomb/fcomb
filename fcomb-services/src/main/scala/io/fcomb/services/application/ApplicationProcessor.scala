package io.fcomb.services.application

import io.fcomb.services.Exceptions._
import io.fcomb.services.node.{NodeProcessor, UserNodeProcessor}
import io.fcomb.models.application.{ApplicationState, ScaleStrategy, Application ⇒ MApplication}
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

  val shardName = "application-processor"

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

  case object WakeUp

  case object ApplicationStart extends Entity

  case object ApplicationStop extends Entity

  case class ApplicationRedeploy(scaleStrategy: Option[ScaleStrategy]) extends Entity

  case class ApplicationScale(numberOfContainers: Int) extends Entity

  case object ApplicationTerminate extends Entity

  case object ApplicationRestart extends Entity

  case class ContainerChangedState(
    id:    Long,
    state: ContainerState.ContainerState
  ) extends Entity

  def props(timeout: Duration) =
    Props(new ApplicationProcessor(timeout))

  case class EntityEnvelope(appId: Long, payload: Any)

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

  def restart(appId: Long)(
    implicit
    timeout: Timeout = Timeout(1.minute)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationRestart, timeout)

  def terminate(appId: Long)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationTerminate, timeout)

  def redeploy(appId: Long, scaleStrategy: Option[ScaleStrategy])(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationRedeploy(scaleStrategy), timeout)

  def scale(appId: Long, numberOfContainers: Int)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationScale(numberOfContainers), timeout)

  def containerChangedState(container: MContainer) =
    tellRef(
      container.applicationId,
      ContainerChangedState(container.getId, container.state)
    )
}

class ApplicationProcessor(timeout: Duration) extends Actor
    with Stash with ActorLogging {
  import context.dispatcher
  import context.system
  import ApplicationProcessor._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val appId = self.path.name.toLong

  case class State(app: MApplication, containers: HashSet[MContainer])

  case class Initialize(state: State, retryCount: Int)

  case class UpdateState(state: State)

  case object Annihilation

  case class Failed(e: Throwable)

  def receive = {
    case msg: Entity ⇒
      log.debug(s"msg: $msg")
      stash()
      initializing()
      context.become({
        case Initialize(state, retryCount) ⇒
          initializeWithState(state, retryCount)
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
        self ! Initialize(state, 1)
      case Failure(e) ⇒ handleThrowable(e)
    }

  def createdReceive(state: State): Receive = {
    case msg: Entity ⇒ msg match {
      case ApplicationStart ⇒
        val replyTo = sender()
        applyState(scale(state, None)) { _ ⇒
          replyTo.!(())
        }
      case ApplicationStop ⇒
        log.error("Can't be stopped")
        sender() ! Status.Failure(new Throwable("Cannot be stopped"))
      case ApplicationRestart ⇒
        log.error("Can't be restarted")
        sender() ! Status.Failure(new Throwable("Cannot be restarted"))
      case ApplicationTerminate ⇒
        val replyTo = sender()
        applyState(terminate(state)) { _ ⇒
          replyTo.!(())
        }
      case _: ApplicationRedeploy ⇒
        log.error("Can't be redeployed")
        sender() ! Status.Failure(new Throwable("Cannot be redeployed"))
      case _: ApplicationScale ⇒
        log.error("Can't be scaled")
        sender() ! Status.Failure(new Throwable("Cannot be scaled"))
      case s: ContainerChangedState ⇒
        log.error(s"Cannot change container when `created` state: $s")
    }
  }

  def runningReceive(state: State): Receive = {
    case msg: Entity ⇒ msg match {
      case ApplicationStart ⇒
        log.debug("Already started")
        sender.!(())
      case ApplicationStop ⇒
        val replyTo = sender()
        applyState(stop(state)) { _ ⇒
          replyTo.!(())
        }
      case ApplicationRestart ⇒
        val replyTo = sender()
        applyState(restart(state)) { _ ⇒
          replyTo.!(())
        }
      case ApplicationTerminate ⇒
        val replyTo = sender()
        applyState(terminate(state)) { _ ⇒
          replyTo.!(())
        }
      case ApplicationRedeploy(scaleStrategy) ⇒
        val replyTo = sender()
        applyState(redeploy(state, scaleStrategy)) { _ ⇒
          replyTo.!(())
        }
      case ApplicationScale(numberOfContainers) ⇒
        val replyTo = sender()
        applyState(scale(state, Some(numberOfContainers))) { _ ⇒
          replyTo.!(())
        }
      case ContainerChangedState(containerId, containerState) ⇒
        // TODO
        sender().!(())
    }
  }

  def stoppedReceive(state: State): Receive = {
    case msg: Entity ⇒ msg match {
      case ApplicationStart ⇒
        val replyTo = sender()
        applyState(start(state)) { _ ⇒
          replyTo.!(())
        }
      case ApplicationStop ⇒
        log.debug("Already stopped")
        sender.!(())
      case ApplicationRestart ⇒
        val replyTo = sender()
        applyState(restart(state)) { _ ⇒
          replyTo.!(())
        }
      case ApplicationTerminate ⇒
        val replyTo = sender()
        applyState(terminate(state)) { _ ⇒
          replyTo.!(())
        }
      case ApplicationRedeploy(scaleStrategy) ⇒
        val replyTo = sender()
        applyState(redeploy(state, scaleStrategy)) { _ ⇒
          replyTo.!(())
        }
      case ApplicationScale(numberOfContainers) ⇒
        val replyTo = sender()
        applyState(scale(state, Some(numberOfContainers))) { _ ⇒
          replyTo.!(())
        }
      case ContainerChangedState(containerId, containerState) ⇒
        // TODO
        sender().!(())
    }
  }

  val terminatedReceive: Receive = {
    case msg: Entity ⇒ msg match {
      case ApplicationTerminate ⇒
        log.debug("Already terminated")
        sender().!(())
      case s ⇒
        log.error(s"Cannot `$s` when terminated state")
        sender() ! Status.Failure(new Throwable(s"Cannot `$s` when terminated state"))
    }
    case ReceiveTimeout ⇒ annihilation()
  }

  def becomeAndUnstash(r: Receive) = {
    context.become(r, false)
    unstashAll()
  }

  def switchReceiveByCompletedState(state: State)(
    handleIncompleted: ApplicationState.ApplicationState ⇒ Unit
  ) =
    state.app.state match {
      case ApplicationState.Created ⇒
        becomeAndUnstash(createdReceive(state))
      case ApplicationState.Running ⇒
        becomeAndUnstash(runningReceive(state))
      case ApplicationState.Stopped ⇒
        becomeAndUnstash(stoppedReceive(state))
      case ApplicationState.Terminated ⇒
        becomeAndUnstash(terminatedReceive)
      case incompletedState ⇒
        handleIncompleted(incompletedState)
    }

  def initializeWithState(state: State, retryCount: Int) = {
    if (retryCount <= 0) {
      log.error(s"No retries")
      annihilation()
    }
    else switchReceiveByCompletedState(state) { incompletedState ⇒
      val newState = incompletedState match {
        case ApplicationState.Starting    ⇒ start(state)
        case ApplicationState.Stopping    ⇒ stop(state)
        case ApplicationState.Restarting  ⇒ restart(state)
        case ApplicationState.Redeploying ⇒ redeploy(state, None)
        case ApplicationState.Scaling     ⇒ scale(state, None)
        case ApplicationState.Terminating ⇒ terminate(state)
        case s ⇒
          val msg = s"This is cannot happen: unknown incompleted `$s` state"
          log.error(msg)
          throw new Throwable(msg)
      }
      newState.map(Initialize(_, retryCount - 1)).pipeTo(self)
    }
  }

  def applyState(stateF: ⇒ Future[State])(
    f: ApplicationState.ApplicationState ⇒ Unit
  ) = {
    context.become({
      case UpdateState(newState) ⇒
        log.info(s"newState: $newState")
        switchReceiveByCompletedState(newState) { incompletedState ⇒
          log.error(s"Cannot apply transition state: $incompletedState")
          annihilation()
        }
      case msg: Entity ⇒
        log.warning(s"stash message: $msg")
        stash()
    }, false)
    // TODO: handle failure state
    stateF.foreach { state ⇒
      self ! UpdateState(state)
      f(state.app.state)
    }
  }

  def getStateByContainers(state: State) = {
    val containers = state.containers.filter(_.isPresent)
    val rl = containers.filter(_.isRunning).size
    val nc = state.app.scaleStrategy.numberOfContainers
    val appState =
      if (rl == nc && nc == containers.size) ApplicationState.Running
      else if (rl > 1) ApplicationState.PartlyRunning
      else ApplicationState.Stopped
    state.copy(app = state.app.copy(state = appState))
  }

  def persistState(state: ApplicationState.ApplicationState) =
    PApplication.updateState(appId, state)

  def updateNumberOfContainers(app: MApplication, numberOfContainers: Int) =
    if (app.scaleStrategy.numberOfContainers == numberOfContainers)
      Future.successful(())
    else
      PApplication.updateNumberOfContainers(appId, numberOfContainers)

  def scale(state: State, numberOpt: Option[Int]) = {
    val containers = state.containers.filter(_.isPresent)
    val numberOfContainers = numberOpt.getOrElse(state.app.scaleStrategy.numberOfContainers)
    if (containers.size == numberOfContainers &&
      state.app.state != ApplicationState.Scaling &&
      state.app.scaleStrategy.numberOfContainers == numberOfContainers)
      Future.successful(state)
    else {
      val updatedState =
        if (state.app.scaleStrategy.numberOfContainers == numberOfContainers)
          state
        else
          state.copy( // TODO: lens
            app = state.app.copy(
              scaleStrategy = state.app.scaleStrategy.copy(
                numberOfContainers = numberOfContainers
              )
            )
          )
      for {
        _ ← updateNumberOfContainers(state.app, numberOfContainers)
        _ ← persistState(ApplicationState.Scaling)
        ns ← scaleContainers(updatedState)
        ss ← start(ns)
        newState = getStateByContainers(ss)
        _ ← persistState(newState.app.state)
      } yield newState
    }
  }

  def updateScaleStrategy(scaleStrategy: Option[ScaleStrategy]) =
    scaleStrategy match {
      case Some(ss) ⇒ PApplication.updateScaleStrategy(appId, ss)
      case None     ⇒ Future.successful(())
    }

  def redeploy(state: State, scaleStrategy: Option[ScaleStrategy]) = {
    if (state.containers.isEmpty &&
      state.app.state != ApplicationState.Redeploying &&
      !scaleStrategy.exists(_ == state.app.scaleStrategy))
      Future.successful(state)
    else {
      val updatedState = scaleStrategy match {
        case Some(ss) ⇒ state.copy( // TODO: lens
          app = state.app.copy(scaleStrategy = ss)
        )
        case None ⇒ state
      }
      for {
        _ ← updateScaleStrategy(scaleStrategy)
        _ ← persistState(ApplicationState.Redeploying)
        ts ← terminateContainers(updatedState)
        ss ← scaleContainers(ts)
        ns ← startContainers(ss)
        newState = getStateByContainers(ns)
        _ ← persistState(newState.app.state)
      } yield newState
    }
  }

  def start(state: State) = {
    if (!state.containers.exists(_.isNotRunning) &&
      state.app.state != ApplicationState.Starting)
      Future.successful(state)
    else
      for {
        _ ← persistState(ApplicationState.Starting)
        ns ← startContainers(state)
        newState = getStateByContainers(ns)
        _ ← persistState(newState.app.state)
      } yield newState
  }

  def stop(state: State) = {
    if (!state.containers.exists(_.isRunning) &&
      state.app.state != ApplicationState.Stopping)
      Future.successful(state)
    else
      for {
        _ ← persistState(ApplicationState.Stopping)
        ns ← stopContainers(state)
        newState = getStateByContainers(ns)
        _ ← persistState(newState.app.state)
      } yield newState
  }

  def restart(state: State) = {
    val containers = state.containers.filter(_.isPresent)
    if (containers.isEmpty && state.app.state != ApplicationState.Restarting)
      Future.successful(state)
    else {
      val ids = containers.map(_.getId)
      val idsSeq = ids.toSeq
      for {
        _ ← persistState(ApplicationState.Restarting)
        _ ← PContainer.updateState(idsSeq, ContainerState.Restarting)
        _ ← Future.sequence(containers.map { c ⇒
          NodeProcessor.containerRestart(c.nodeId.get, c.getId)
        })
        _ ← PContainer.updateState(idsSeq, ContainerState.Running)
        newState = getStateByContainers(state.copy(
          containers = state.containers.map { c ⇒
            if (ids.contains(c.getId)) c.copy(state = ContainerState.Running)
            else c
          }
        ))
        _ ← persistState(newState.app.state)
      } yield newState
    }
  }

  def terminate(state: State) = {
    if (state.containers.exists(!_.isTerminated)) {
      for {
        _ ← persistState(ApplicationState.Terminating)
        ns ← terminateContainers(state)
        _ ← persistState(ApplicationState.Terminated)
      } yield ns.copy(app = ns.app.copy(state = ApplicationState.Terminated))
    }
    else {
      if (state.app.state == ApplicationState.Terminated)
        Future.successful(state)
      else
        persistState(ApplicationState.Terminated).map { _ ⇒
          state.copy(app = state.app.copy(state = ApplicationState.Terminated))
        }
    }
  }

  def startContainers(state: State) = {
    val containers = state.containers.filter(_.isNotRunning)
    if (containers.isEmpty) Future.successful(state)
    else {
      val ids = containers.map(_.getId)
      val idsSeq = ids.toSeq
      for {
        _ ← PContainer.updateState(idsSeq, ContainerState.Starting)
        _ ← Future.sequence(containers.map { c ⇒
          NodeProcessor.containerStart(c.nodeId.get, c.getId)
        })
        _ ← PContainer.updateState(idsSeq, ContainerState.Running)
      } yield {
        val runningContainers = state.containers.map { c ⇒
          if (ids.contains(c.getId)) c.copy(state = ContainerState.Running)
          else c
        }
        state.copy(containers = runningContainers)
      }
    }
  }

  def stopContainers(state: State) = {
    val containers = state.containers.filter(_.isRunning)
    if (containers.isEmpty) Future.successful(state)
    else {
      val ids = containers.map(_.getId)
      val idsSeq = ids.toSeq
      for {
        _ ← PContainer.updateState(idsSeq, ContainerState.Stopping)
        _ ← Future.sequence(containers.map { c ⇒
          NodeProcessor.containerStop(c.nodeId.get, c.getId)
        })
        _ ← PContainer.updateState(idsSeq, ContainerState.Stopped)
      } yield {
        val stoppedContainers = state.containers.map { c ⇒
          if (ids.contains(c.getId)) c.copy(state = ContainerState.Stopped)
          else c
        }
        state.copy(containers = stoppedContainers)
      }
    }
  }

  def scaleContainers(state: State) = {
    // default emptiest node strategy
    val ss = state.app.scaleStrategy
    val app = state.app
    val containers = state.containers.toList
      .filter(_.isPresent)
      .sortBy(_.number)
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
          UserNodeProcessor.containerCreate(c, app.image, app.deployOptions)
        })
        updatedContainers ← PContainer.batchPartialUpdate(createdContainers)
      } yield state.copy(containers = state.containers ++ updatedContainers)
    }
    else if (containers.length > ss.numberOfContainers) {
      val terminateContainers = containers.drop(ss.numberOfContainers)
      val aliveContainers = containers.take(ss.numberOfContainers)
      val ids = terminateContainers.map(_.getId)
      for {
        _ ← PContainer.updateState(ids, ContainerState.Terminating)
        _ ← Future.sequence(terminateContainers.map { c ⇒
          NodeProcessor.containerTerminate(c.nodeId.get, c.getId)
        })
        _ ← PContainer.updateState(ids, ContainerState.Terminated)
      } yield state.copy(containers = HashSet(aliveContainers: _*))
    }
    else Future.successful(state)
  }

  def terminateContainers(state: State) = {
    val containers = state.containers.filterNot(_.isTerminated)
    val ids = containers.map(_.getId)
    val idsSeq = ids.toSeq
    for {
      _ ← PContainer.updateState(idsSeq, ContainerState.Terminating)
      _ ← Future.sequence(containers.map { c ⇒
        NodeProcessor.containerTerminate(c.nodeId.get, c.getId)
      })
      _ ← PContainer.updateState(idsSeq, ContainerState.Terminated)
    } yield state.copy(containers = HashSet.empty)
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
