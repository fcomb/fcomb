package io.fcomb.services

import akka.actor._
import akka.stream.Materializer
import akka.cluster.sharding._
import akka.pattern.ask
import akka.util.Timeout
import io.fcomb.services.Exceptions._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Failure}
import org.slf4j.LoggerFactory

object ProcessorMessages {

}

sealed trait EntityMessage

trait Processor[Id] {
  lazy val logger = LoggerFactory.getLogger(getClass)

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
    ec: ExecutionContext,
    m:  Manifest[T]
  ): Future[T] =
    Option(actorRef) match {
      case Some(ref) ⇒
        ask(ref, EntityEnvelope(id, entity))(timeout)
          .mapTo[T]
          .recover {
            case e: Throwable ⇒
              logger.error(s"ask ref $id#$entity error: $e")
              throw e
          }
      case None ⇒ Future.failed(EmptyActorRefException)
    }

  protected def tellRef(id: Id, entity: Any) =
    Option(actorRef).map(_ ! EntityEnvelope(id, entity))
}

object ProcessorActorMessages {
  case object Annihilation

  case object Unstash

  case class Failed(e: Throwable)

  case class UpdateState[S](state: S)
}

trait ProcessorActor[S] {
  this: Actor with Stash with ActorLogging ⇒

  import context.dispatcher
  import ProcessorActorMessages._
  import ShardRegion.Passivate

  private[this] var _state: S = getInitialState

  protected def getInitialState: S

  protected final def getState: S = this._state

  def modifyState(f: S ⇒ S): S = {
    val state = f(getState)
    putState(state)
    state
  }

  def putState(state: S) = {
    this._state = state
  }

  val stashReceive: Receive = {
    case Unstash ⇒
      context.unbecome()
      unstashAll()
    case msg ⇒
      log.warning(s"stash message: $msg")
      stash()
  }

  def becomeStashing() = {
    context.become(stashReceive, true)
  }

  def unstashReceive() = {
    self ! Unstash
  }

  def putStateAsync(fut: Future[S]): Future[S] =
    putStateWithReceiveAsyncFunc(fut)(None)

  def putStateWithReceiveAsync(fut: Future[S])(rf: S ⇒ Receive): Future[S] =
    putStateWithReceiveAsyncFunc(fut)(Some(rf))

  private def putStateWithReceiveAsyncFunc(fut: Future[S])(
    rfOpt: Option[S ⇒ Receive]
  ) = {
    val p = Promise[S]()
    context.become({
      case UpdateState(res) ⇒
        val state = res.asInstanceOf[S]
        putState(state)
        rfOpt match {
          case Some(receiveF) ⇒
            context.become(receiveF(state))
          case None ⇒
            context.unbecome()
        }
        unstashAll()
        p.complete(Success(state))
      case msg ⇒
        log.warning(s"stash message: $msg")
        stash()
    }, rfOpt.isEmpty)
    fut.onComplete {
      case Success(s) ⇒ self ! UpdateState(s)
      case Failure(e) ⇒
        p.failure(e)
        handleThrowable(e)
    }
    p.future
  }

  def stashAsync[T](fut: Future[T]): Future[T] = {
    becomeStashing()
    fut.onComplete {
      case Success(res) ⇒ unstashReceive()
      case Failure(e)   ⇒ handleThrowable(e)
    }
    fut
  }

  def failed(e: Throwable): Receive = {
    case _: EntityMessage ⇒ sender ! Status.Failure(e)
    case Annihilation     ⇒ annihilation()
  }

  val handleFailure: PartialFunction[Throwable, Any] = {
    case e: Throwable ⇒ handleThrowable(e)
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
