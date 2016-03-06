package io.fcomb.docker.distribution.server.services

import io.fcomb.services._
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

object ImagePushProcessor extends Processor[UUID] {
  val numberOfShards = 1

  val shardName = "image-push-processor"

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
    Props(new ImagePushProcessor(timeout))

  case object GetState extends Entity

  case class PushDataBytes(
    range: (Long, Long),
    dataBytes: Source[ByteString, Any]
  ) extends Entity

  // def getState(imageUuid: UUID)(
  //   implicit
  //   ec:      ExecutionContext,
  //   timeout: Timeout          = Timeout(30.seconds)
  // ): Future[ReserveResult] =
  //   askRef[ReserveResult](userId, Reserve(scaleStrategy), timeout)
}

object ImagePushMessages {
  case class State(
    length: Long,
    digest: MessageDigest
  )
}

class ImagePushProcessor(timeout: Duration) extends Actor
    with Stash with ActorLogging with ProcessorActor[ImagePushMessages.State] {
  import context.dispatcher
  import ImagePushProcessor._
  import ImagePushMessages._

  context.setReceiveTimeout(timeout)

  val imageUuid = self.path.name

  def getInitialState() = State(0L, MessageDigest.getInstance("SHA256"))

  def receive = ???
}
