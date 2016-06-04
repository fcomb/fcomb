package io.fcomb.docker.distribution.server.services

import io.fcomb.utils.StringUtils
import akka.actor._
import akka.cluster.sharding._
import akka.stream.{Materializer, ClosedShape}
import akka.stream.scaladsl._
import akka.util.{ByteString, Timeout}
import cats.data.Xor
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.security.MessageDigest
import java.util.UUID
import java.nio.file.StandardOpenOption
import java.io.File

import akka.actor._
import akka.stream.Materializer
import akka.cluster.sharding._
import akka.pattern.ask
import akka.util.Timeout
import io.fcomb.services.Exceptions._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Success, Failure}
import org.slf4j.LoggerFactory

sealed trait EntityMessage

trait ProcessorClustedSharding[Id] {
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

object ImageBlobPushProcessor extends ProcessorClustedSharding[UUID] {
  val numberOfShards = 1

  val shardName = "image-blob-push-processor"

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) ⇒ id.toString
  }

  def startRegion(timeout: Duration)(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) =
    startClustedSharding(props(timeout))

  def props(timeout: Duration)(implicit mat: Materializer) =
    Props(new ImageBlobPushProcessor(timeout))

  def uploadChunk(blobId: UUID, source: Source[ByteString, Any], file: File)(
    implicit
    ec:  ExecutionContext,
    mat: Materializer
  ): Future[(Long, String)] = {
    for {
      Xor.Right(md) ← begin(blobId)
      (length, chunkDigest) ← uploadChunkGraph(md, source, file).run()
      Xor.Right(fileDigest) ← commit(blobId, chunkDigest)
    } yield (length, StringUtils.hexify(fileDigest.digest))
  }

  private def uploadChunkGraph(
    md:     MessageDigest,
    source: Source[ByteString, Any],
    file:   File
  ) = {
    val fileOptions = Set(StandardOpenOption.APPEND, StandardOpenOption.CREATE)

    val sink = Sink.fold[(Long, MessageDigest), ByteString]((0L, md)) {
      case ((length, md), bs) ⇒
        md.update(bs.toArray)
        (length + bs.length, md)
    }

    RunnableGraph
      .fromGraph(GraphDSL.create(source.completionTimeout(25.minutes), sink)(
        Keep.right
      ) { implicit b ⇒ (source, sink) ⇒
        import GraphDSL.Implicits._

        val broadcast = b.add(Broadcast[ByteString](2))

        source ~> broadcast.in

        broadcast.out(0) ~> FileIO.toPath(file.toPath, fileOptions)
        broadcast.out(1) ~> sink

        ClosedShape
      })
  }

  final case object Begin extends Entity

  final case object Stop extends Entity

  final case class Commit(md: MessageDigest) extends Entity

  def begin(blobId: UUID)(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(30.seconds)
  ): Future[Xor[String, MessageDigest]] =
    askRef[Xor[String, MessageDigest]](blobId, Begin, timeout)

  def commit(blobId: UUID, md: MessageDigest)(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(30.seconds)
  ): Future[Xor[String, MessageDigest]] =
    askRef[Xor[String, MessageDigest]](blobId, Commit(md), timeout)

  def stop(blobId: UUID)(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(30.seconds)
  ) =
    askRef[Xor[String, Unit]](blobId, Stop, timeout)
}

object ProcessorActorMessages {
  final case object Annihilation
  final case object Unstash
  final case class Failed(e: Throwable)
  final case class UpdateState[S](state: S)
}

object ImageBlobPushMessages {
  @SerialVersionUID(1L)
  final case class State(digest: MessageDigest)
}

trait ProcessorActor[S] extends Stash with ActorLogging { this: Actor ⇒
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

import ImageBlobPushMessages._

class ImageBlobPushProcessor(timeout: Duration) extends Actor with ActorLogging {
  import context.dispatcher
  import ImageBlobPushProcessor._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val imageUuid = self.path.name

  private var state: State = State(MessageDigest.getInstance("SHA-256"))

  def updateState(digest: MessageDigest) =
    state = state.copy(digest = digest.clone.asInstanceOf[MessageDigest])

  val idle: Receive = {
    case Begin ⇒
      context.become(locking, false)
      sender() ! Xor.Right(state.digest.clone.asInstanceOf[MessageDigest])
    case Commit(_) ⇒
      sender() ! Xor.Left("Transaction not being started")
    case Stop ⇒
      sender() ! Xor.Right(())
      context.parent ! Passivate(stopMessage = PoisonPill)
  }

  val locking: Receive = {
    case Begin ⇒
      sender() ! Xor.Left("Transaction already started")
    case Commit(md) ⇒
      context.become(idle)
      updateState(md)
      sender() ! Xor.Right(state.digest.clone.asInstanceOf[MessageDigest])
    case Stop ⇒
      sender() ! Xor.Left("The transaction is not completed yet")
  }

  def receive = idle
}
