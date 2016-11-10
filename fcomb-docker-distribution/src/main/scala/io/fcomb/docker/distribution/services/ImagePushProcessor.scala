/*
 * Copyright 2016 fcomb. <https://fcomb.io>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fcomb.docker.distribution.services

import akka.actor._
import akka.cluster.sharding._
import akka.pattern.ask
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.stream.{ClosedShape, Materializer}
import akka.util.Timeout
import akka.util.{ByteString, Timeout}
import com.typesafe.scalalogging.LazyLogging
import io.fcomb.services.Exceptions._
import io.fcomb.utils.StringUtils
import java.io.File
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.UUID
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

sealed trait EntityMessage

trait ProcessorClustedSharding[Id] extends LazyLogging {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(id, payload) => (id.toString, payload)
  }

  val extractShardId: ShardRegion.ExtractShardId

  val numberOfShards: Int

  val shardName: String

  trait Entity extends EntityMessage

  case class EntityEnvelope(id: Id, payload: Any)

  private var actorRef: ActorRef = _

  protected def startClustedSharding(props: Props)(
      implicit sys: ActorSystem,
      mat: Materializer
  ): ActorRef = {
    if (actorRef eq null) {
      actorRef = ClusterSharding(sys).start(
        typeName = shardName,
        entityProps = props,
        settings = ClusterShardingSettings(sys),
        extractEntityId = extractEntityId,
        extractShardId = extractShardId
      )
    }
    actorRef
  }

  protected def askRef[T](id: Id, entity: Entity, timeout: Timeout)(implicit ec: ExecutionContext,
                                                                    m: ClassTag[T]): Future[T] =
    Option(actorRef) match {
      case Some(ref) =>
        ask(ref, EntityEnvelope(id, entity))(timeout).mapTo[T].recover {
          case e: Throwable =>
            logger.error(s"ask ref $id#$entity error: $e")
            throw e
        }
      case None => Future.failed(EmptyActorRefException)
    }

  protected def tellRef(id: Id, entity: Any) =
    Option(actorRef).map(_ ! EntityEnvelope(id, entity))
}

object ImageBlobPushProcessor extends ProcessorClustedSharding[UUID] {
  val numberOfShards = 1

  val shardName = "image-blob-push-processor"

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) => id.toString
  }

  def startRegion(timeout: Duration)(
      implicit sys: ActorSystem,
      mat: Materializer
  ) =
    startClustedSharding(props(timeout))

  def props(timeout: Duration)(implicit mat: Materializer) =
    Props(new ImageBlobPushProcessor(timeout))

  def uploadChunk(blobId: UUID, source: Source[ByteString, Any], file: File)(
      implicit ec: ExecutionContext,
      mat: Materializer
  ): Future[(Long, String)] =
    for {
      Right(md)             <- begin(blobId)
      (length, chunkDigest) <- uploadChunkGraph(md, source, file).run()
      Right(fileDigest)     <- commit(blobId, chunkDigest)
    } yield (length, StringUtils.hexify(fileDigest.digest))

  private def uploadChunkGraph(md: MessageDigest, source: Source[ByteString, Any], file: File) = {
    val fileOptions = Set(StandardOpenOption.APPEND, StandardOpenOption.CREATE)

    val sink = Sink.fold[(Long, MessageDigest), ByteString]((0L, md)) {
      case ((length, md), bs) =>
        md.update(bs.toArray)
        (length + bs.length, md)
    }

    RunnableGraph.fromGraph(
      GraphDSL.create(source.completionTimeout(25.minutes), sink)(Keep.right) {
        implicit b => (source, sink) =>
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
      implicit ec: ExecutionContext,
      timeout: Timeout = Timeout(30.seconds)): Future[Either[String, MessageDigest]] =
    askRef[Either[String, MessageDigest]](blobId, Begin, timeout)

  def commit(blobId: UUID, md: MessageDigest)(
      implicit ec: ExecutionContext,
      timeout: Timeout = Timeout(30.seconds)
  ): Future[Either[String, MessageDigest]] =
    askRef[Either[String, MessageDigest]](blobId, Commit(md), timeout)

  def stop(blobId: UUID)(
      implicit ec: ExecutionContext,
      timeout: Timeout = Timeout(30.seconds)
  ) =
    askRef[Either[String, Unit]](blobId, Stop, timeout)
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

trait ProcessorActor[S] extends Stash with ActorLogging { this: Actor =>
  import context.dispatcher
  import ProcessorActorMessages._
  import ShardRegion.Passivate

  private[this] var _state: S = getInitialState

  protected def getInitialState: S

  protected final def getState: S = this._state

  def modifyState(f: S => S): S = {
    val state = f(getState)
    putState(state)
    state
  }

  def putState(state: S) =
    this._state = state

  val stashReceive: Receive = {
    case Unstash =>
      context.unbecome()
      unstashAll()
    case msg =>
      log.warning(s"stash message: {}", msg)
      stash()
  }

  def becomeStashing() =
    context.become(stashReceive, true)

  def unstashReceive() =
    self ! Unstash

  def putStateAsync(fut: Future[S]): Future[S] =
    putStateWithReceiveAsyncFunc(fut)(None)

  def putStateWithReceiveAsync(fut: Future[S])(rf: S => Receive): Future[S] =
    putStateWithReceiveAsyncFunc(fut)(Some(rf))

  private def putStateWithReceiveAsyncFunc(fut: Future[S])(
      rfOpt: Option[S => Receive]
  ) = {
    val p = Promise[S]()
    context.become({
      case UpdateState(res) =>
        val state = res.asInstanceOf[S]
        putState(state)
        rfOpt match {
          case Some(receiveF) =>
            context.become(receiveF(state))
          case None =>
            context.unbecome()
        }
        unstashAll()
        p.complete(Success(state))
        ()
      case msg =>
        log.warning(s"stash message: {}", msg)
        stash()
    }, rfOpt.isEmpty)
    fut.onComplete {
      case Success(s) => self ! UpdateState(s)
      case Failure(e) =>
        p.failure(e)
        handleThrowable(e)
    }
    p.future
  }

  def stashAsync[T](fut: Future[T]): Future[T] = {
    becomeStashing()
    fut.onComplete {
      case Success(res) => unstashReceive()
      case Failure(e)   => handleThrowable(e)
    }
    fut
  }

  def failed(e: Throwable): Receive = {
    case _: EntityMessage => sender ! Status.Failure(e)
    case Annihilation     => annihilation()
  }

  val handleFailure: PartialFunction[Throwable, Any] = {
    case e: Throwable => handleThrowable(e)
  }

  def handleThrowable(e: Throwable): Unit = {
    log.error(e, e.getMessage())
    context.become({
      case Failed(e) =>
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

final class ImageBlobPushProcessor(timeout: Duration) extends Actor with ActorLogging {
  import ImageBlobPushProcessor._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val imageUuid = self.path.name

  private var state: State = State(MessageDigest.getInstance("SHA-256"))

  def updateState(digest: MessageDigest) =
    state = state.copy(digest = digest.clone.asInstanceOf[MessageDigest])

  val idle: Receive = {
    case Begin =>
      context.become(locking, false)
      sender() ! Right(state.digest.clone.asInstanceOf[MessageDigest])
    case Commit(_) => sender() ! Left("Transaction not being started")
    case Stop =>
      sender() ! Right(())
      context.parent ! Passivate(stopMessage = PoisonPill)
  }

  val locking: Receive = {
    case Begin => sender() ! Left("Transaction already started")
    case Commit(md) =>
      context.become(idle)
      updateState(md)
      sender() ! Right(state.digest.clone.asInstanceOf[MessageDigest])
    case Stop => sender() ! Left("The transaction is not completed yet")
  }

  def receive = idle
}
