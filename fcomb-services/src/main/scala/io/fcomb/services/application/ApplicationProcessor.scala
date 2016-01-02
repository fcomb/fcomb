package io.fcomb.services.application

import io.fcomb.services.Exceptions._
import io.fcomb.services.docker.ContainerManager
import io.fcomb.models.application.{Application ⇒ MApplication}
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
    case ApplicationStart ⇒
      log.info(s"start application: $app")
      ContainerManager.create(app)
    // case cmd: DockerApiCommands ⇒ cmd match {
    //   case DockerPing ⇒
    //     apiClient.ping().onComplete(println)
    // }
    // TODO: case ReceiveTimeout ⇒ suicide()
  }

  def failed(e: Throwable): Receive = {
    case _: Entity ⇒ sender ! Status.Failure(e)
    case Stop      ⇒ suicide()
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

  def suicide() = {
    log.info("suicide!")
    context.parent ! Passivate(stopMessage = PoisonPill)
  }
}
