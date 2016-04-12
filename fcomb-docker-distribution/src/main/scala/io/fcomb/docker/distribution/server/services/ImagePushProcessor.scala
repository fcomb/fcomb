package io.fcomb.docker.distribution.server.services

import io.fcomb.services._
import io.fcomb.utils.StringUtils
import akka.actor._
import akka.cluster.sharding._
import akka.stream.{Materializer, ClosedShape}
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.util.{ByteString, Timeout}
import cats.data.Xor
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.security.MessageDigest
import java.util.UUID
import java.nio.file.StandardOpenOption
import java.io.File

object ImageBlobPushProcessor extends Processor[UUID] {
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

  private def uploadChunkGraph(md: MessageDigest, source: Source[ByteString, Any], file: File) = {
    val fileOptions = Set(StandardOpenOption.APPEND, StandardOpenOption.CREATE)

    val sink = Sink.fold[(Long, MessageDigest), ByteString]((0L, md)) {
      case ((length, md), bs) ⇒
        md.update(bs.toArray)
        (length + bs.length, md)
    }

    RunnableGraph.fromGraph(GraphDSL.create(source.completionTimeout(25.minutes), sink)(Keep.right) { implicit b ⇒ (source, sink) ⇒
      import GraphDSL.Implicits._

      val broadcast = b.add(Broadcast[ByteString](2))

      source ~> broadcast.in

      broadcast.out(0) ~> FileIO.toFile(file, fileOptions)
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

object ImageBlobPushMessages {
  case class State(digest: MessageDigest)
}

class ImageBlobPushProcessor(timeout: Duration) extends Actor with ActorLogging {
  import context.dispatcher
  import ImageBlobPushProcessor._
  import ImageBlobPushMessages._
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
