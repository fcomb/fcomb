package io.fcomb.services

import akka.actor._
import akka.stream.Materializer
import akka.cluster.sharding._
import akka.pattern.ask
import akka.util.Timeout
import io.fcomb.services.Exceptions._
import scala.concurrent.Future
import scala.util.{Success, Failure}

object ProcessorMessages {

}

sealed trait EntityMessage

trait Processor[Id] {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) ⇒ (id.toString, payload)
  }

  val extractShardId: ShardRegion.ExtractShardId

  val numberOfShards: Int

  val shardName: String

  trait Entity extends EntityMessage

  case class EntityEnvelope(id: Id, payload: Any)

  private var actorRef: ActorRef = _

  protected def startClustedSharding(props: Props)(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) = {
    val ref = ClusterSharding(sys).start(
      typeName = shardName,
      entityProps = props,
      settings = ClusterShardingSettings(sys),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
    actorRef = ref
    ref
  }

  protected def askRef[T](
    id:      Id,
    entity:  Entity,
    timeout: Timeout
  )(
    implicit
    m: Manifest[T]
  ): Future[T] =
    Option(actorRef) match {
      case Some(ref) ⇒
        ask(ref, EntityEnvelope(id, entity))(timeout).mapTo[T]
      case None ⇒ Future.failed(EmptyActorRefException)
    }

  protected def tellRef(id: Id, entity: Any) =
    Option(actorRef).map(_ ! EntityEnvelope(id, entity))
}

object ProcessorActorMessages {
  case object Annihilation

  case class Failed(e: Throwable)

  case class Initialize[S](state: S)
}

trait ProcessorActor[S] {
  this: Actor with Stash with ActorLogging ⇒

  import context.dispatcher
  import ProcessorActorMessages._
  import ShardRegion.Passivate

  def initializing(): Unit

  def stateReceive(state: S): Receive

  def initialize(state: S) = {
    self ! Initialize(state)
  }

  def receive: Receive = {
    case msg: EntityMessage ⇒
      stash()
      context.become({
        case Initialize(state) ⇒
          context.become(stateReceive(state.asInstanceOf[S]), false)
          unstashAll()
        case msg: EntityMessage ⇒
          log.warning(s"stash message: $msg")
          stash()
      }, false)
      initializing()
  }

  def updateStateSync[T](fut: Future[T])(f: T ⇒ S) = {
    context.become({
      case Initialize(res) ⇒
        context.become(stateReceive(f(res.asInstanceOf[T])), false)
        unstashAll()
      case msg: EntityMessage ⇒
        log.warning(s"stash message: $msg")
        stash()
    }, false)
    fut.onComplete {
      case Success(res) ⇒ self ! Initialize(res)
      case Failure(e)   ⇒ handleThrowable(e)
    }
  }

  def failed(e: Throwable): Receive = {
    case _: EntityMessage ⇒ sender ! Status.Failure(e)
    case Annihilation     ⇒ annihilation()
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
