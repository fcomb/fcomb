package io.fcomb.services.application

import io.fcomb.services.Exceptions._
import io.fcomb.services.node.{NodeProcessor, UserNodesProcessor}
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

  case object ApplicationStop extends Entity

  case object ApplicationRedeploy extends Entity

  case class ApplicationScale(count: Int) extends Entity

  case object ApplicationTerminate extends Entity

  case class NewContainerState(
    id:    Long,
    state: ContainerState.ContainerState
  ) extends Entity

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
    timeout: Timeout = Timeout(5.minutes)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationStart, timeout)

  def stop(appId: Long)(
    implicit
    timeout: Timeout = Timeout(5.minutes)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationStop, timeout)

  def terminate(appId: Long)(
    implicit
    timeout: Timeout = Timeout(5.minutes)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationTerminate, timeout)

  def redeploy(appId: Long)(
    implicit
    timeout: Timeout = Timeout(5.minutes)
  ): Future[Unit] =
    askRef[Unit](appId, ApplicationRedeploy, timeout)

  def scale(appId: Long, count: Int)(
    implicit
    timeout: Timeout = Timeout(5.minutes)
  ): Future[Unit] =
    askRef(appId, ApplicationScale(count), timeout)

  def newContainerState(
    appId:       Long,
    containerId: Long,
    state:       ContainerState.ContainerState
  ) =
    tellRef(appId, NewContainerState(containerId, state))
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

  case object Annihilation

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
        if (app.state == ContainerState.Initializing)
          UserNodesProcessor.createContainers(app)
        else {
          import io.fcomb.persist.docker.Container
          Container.findAllByApplicationId(appId).flatMap { cs ⇒
            Future.sequence(cs.map { c ⇒
              NodeProcessor.startContainer(c.nodeId, c.getId)
            }).map(_ => ())
          }.pipeTo(sender())
        }
      case ApplicationStop ⇒
        log.info(s"stop application: $app")
        import io.fcomb.persist.docker.Container
        Container.findAllByApplicationId(appId).flatMap { cs ⇒
          Future.sequence(cs.map { c ⇒
            NodeProcessor.stopContainer(c.nodeId, c.getId)
          }).map(_ => ())
        }.pipeTo(sender())
      case ApplicationRedeploy ⇒
        log.info(s"redeploy application: $app")
        sender().!(())
      case ApplicationScale(count) ⇒
        log.info(s"scale application: $app")
        sender().!(())
      case ApplicationTerminate ⇒
        log.info(s"terminate application: $app")
        sender().!(())
      case NewContainerState(containerId, containerState) ⇒
        println(s"NewContainerState($containerId, $containerState)")
        val napp = app.copy(state = ApplicationState.Running)
        PApplication.updateState(appId, ApplicationState.Running)
        context.become(initialized(napp), false)
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
