package io.fcomb.docker.distribution.server.services

import io.fcomb.services._
import io.fcomb.models.docker.distribution.{BlobState}
import akka.actor._
import akka.cluster.sharding._
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.Directives._
import akka.util.{ByteString, Timeout}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import java.security.MessageDigest
import java.util.UUID

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

  case object GetState extends Entity

  // def getState(imageUuid: UUID)(
  //   implicit
  //   ec:      ExecutionContext,
  //   timeout: Timeout          = Timeout(30.seconds)
  // ): Future[ReserveResult] =
  //   askRef[ReserveResult](userId, Reserve(scaleStrategy), timeout)
}

object ImageBlobPushMessages {
  case class State(
    length: Long,
    digest: MessageDigest
  )
}

class ImageBlobPushProcessor(timeout: Duration) extends Actor
    with Stash with ActorLogging with ProcessorActor[ImageBlobPushMessages.State] {
  import context.dispatcher
  import ImageBlobPushProcessor._
  import ImageBlobPushMessages._

  context.setReceiveTimeout(timeout)

  val imageUuid = self.path.name

  def getInitialState() = State(0L, MessageDigest.getInstance("SHA256"))

  def receive = ???
}
