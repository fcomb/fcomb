package io.fcomb.services.node

import io.fcomb.services.Exceptions._
import io.fcomb.services._
import io.fcomb.services.application.ApplicationProcessor
import io.fcomb.models.application.{DockerImage, DockerDeployOptions, Application ⇒ MApplication}
import io.fcomb.models.errors._
import io.fcomb.models.docker.{ContainerState, Container ⇒ MContainer}
import io.fcomb.models.node.{NodeState, Node ⇒ MNode}
import io.fcomb.persist.docker.{Container ⇒ PContainer}
import io.fcomb.utils.{Config, Implicits, Random}
import io.fcomb.crypto.{Certificate, Tls}
import io.fcomb.persist.node.{Node ⇒ PNode}
import io.fcomb.persist.UserCertificate
import io.fcomb.docker.api._, methods.ContainerMethods
import akka.actor._
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.cluster.sharding._
import akka.pattern.{after, ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.collection.immutable.{LongMap, HashMap}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scalaz._, Scalaz._
import java.time.{LocalDateTime, ZonedDateTime}
import java.net.InetAddress

object NodeProcessor extends Processor[Long] {
  val numberOfShards = 1

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(id, _) ⇒ (id % numberOfShards).toString
  }

  val shardName = "node-processor"

  def startRegion(timeout: Duration)(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) =
    startClustedSharding(props(timeout))

  // TODO: start nodes from user nodes processor instead
  def initialize()(
    implicit
    ec: ExecutionContext
  ) =
    PNode.findAllNonTerminated().map(_.foreach { node ⇒
      tellRef(node.getId, WakeUp)
    })

  case object WakeUp

  case class NodeRegister(ip: InetAddress) extends Entity

  case class ContainerCreate(
    container:     MContainer,
    image:         DockerImage,
    deployOptions: DockerDeployOptions
  ) extends Entity

  case class ContainerStart(id: Long) extends Entity

  case class ContainerStop(id: Long) extends Entity

  case class ContainerTerminate(id: Long) extends Entity

  case class ContainerRestart(id: Long) extends Entity

  def props(timeout: Duration)(implicit mat: Materializer) =
    Props(new NodeProcessor(timeout))

  def register(nodeId: Long, ip: InetAddress)(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](nodeId, NodeRegister(ip), timeout)

  def containerCreate(
    container:     MContainer,
    image:         DockerImage,
    deployOptions: DockerDeployOptions
  )(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(15.minutes)
  ): Future[MContainer] =
    askRef[MContainer](container.nodeId, ContainerCreate(container, image, deployOptions), timeout)

  def containerStart(nodeId: Long, containerId: Long)(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](nodeId, ContainerStart(containerId), timeout)

  def containerStop(nodeId: Long, containerId: Long)(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(1.minute)
  ): Future[Unit] =
    askRef[Unit](nodeId, ContainerStop(containerId), timeout)

  def containerRestart(nodeId: Long, containerId: Long)(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(1.minute)
  ): Future[Unit] =
    askRef[Unit](nodeId, ContainerRestart(containerId), timeout)

  def containerTerminate(nodeId: Long, containerId: Long)(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(1.minute)
  ): Future[Unit] =
    askRef[Unit](nodeId, ContainerTerminate(containerId), timeout)
}

object NodeProcessorMessages {
  case class DockerApiCerts(
    key:  Array[Byte],
    cert: Array[Byte],
    ca:   Array[Byte]
  )

  case class NodeImages(
    pullingCache: HashMap[String, (LocalDateTime, Promise[Unit])]
  )

  case class State(
    apiClient:  Option[Client],
    node:       MNode,
    containers: HashMap[Long, MContainer],
    images:     NodeImages,
    certs:      DockerApiCerts
  )

  private[node] sealed trait NodeProcessorMessage

  private[node] case object DockerPing extends NodeProcessorMessage

  private[node] case class UpdateContainer(
    container: MContainer
  ) extends NodeProcessorMessage
}

class NodeProcessor(timeout: Duration)(implicit mat: Materializer) extends Actor
    with Stash with ActorLogging with ProcessorActor[NodeProcessorMessages.State] {
  import context.dispatcher
  import context.system
  import NodeProcessor._
  import NodeProcessorMessages._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val nodeId = self.path.name.toLong
  // TODO: val dockerApiTimeout = 1.minute
  val pingInterval = (25 + Random.random.nextInt(15)).seconds

  val pullingCacheTtl = 5.minutes.toSeconds // TODO: drop TTL and check images locally or remote

  system.scheduler.schedule(pingInterval, pingInterval)(self ! DockerPing)

  def initializing() = {
    (for {
      // TODO: add OptionT
      Some(node) ← PNode.findByPk(nodeId)
      Some((rootCert, clientCert)) ← UserCertificate
        .findRootAndClientCertsByUserId(node.userId)
      containers ← PContainer.findAllByNodeId(nodeId)
    } yield {
      val certs = DockerApiCerts(
        clientCert.key,
        clientCert.certificate,
        rootCert.certificate
      )
      val apiClient = createApiClient(node, certs)
      val cs = HashMap(containers.map(c ⇒ (c.getId, c)): _*)
      val images = NodeImages(HashMap.empty)
      initialize(State(apiClient, node, cs, images, certs))
    }).onFailure {
      case e ⇒ handleThrowable(e)
    }
  }

  def stateReceive(state: State): Receive = {
    case msg: Entity ⇒
      val replyTo = sender()
      msg match {
        case NodeRegister(ip) ⇒
          nodeRegister(state, ip).map(sendReplyByState(_, replyTo))
        case ContainerCreate(container, image, deployOptions) ⇒
          println(s"CreateContainer($container, $image, $deployOptions)")
          val (ns, resFut) = containerCreate(state, container, image, deployOptions)
          // TODO
          resFut.onComplete {
            case Success(c) ⇒
              self ! UpdateContainer(c)
              replyTo ! c
            case Failure(e) ⇒
              val c = container.copy(state = ContainerState.Terminated)
              self ! UpdateContainer(c)
              replyTo ! c
          }
          updateContextByState(ns)
        case ContainerStart(containerId) ⇒
          containerStart(state, containerId)
        case ContainerStop(containerId) ⇒
          containerStop(state, containerId)
        case ContainerRestart(containerId) ⇒
          containerRestart(state, containerId)
        case ContainerTerminate(containerId) ⇒
          log.debug(s"TerminateContainer($containerId)")
          containerTerminate(state, containerId)
      }
    case cmd: NodeProcessorMessage ⇒ cmd match {
      case DockerPing ⇒
        apiCall(state)(_.ping()).onComplete(println)
      case UpdateContainer(container) ⇒
        println(s"update container: $container")
        updateContextByState(updateStateContainer(state, container))
    }
    case ReceiveTimeout ⇒
      if (state.containers.isEmpty) annihilation()
  }

  def updateStateContainer(state: State, container: MContainer) =
    state.copy(
      containers = state.containers + ((container.getId, container))
    )

  def updateContextByState(state: State) =
    context.become(stateReceive(state), false)

  def nodeRegister(state: State, ip: InetAddress) = {
    log.info(s"register $ip")
    if (state.node.publicIpInetAddress().contains(ip)) {
      if (state.node.state == NodeState.Pending)
        updateStateSync(checkAndUpdateState(state))(identity)
      else Future.successful(state)
    }
    else {
      val ipAddress = ip.getHostAddress
      val node = state.node.copy(publicIpAddress = Some(ipAddress))
      val apiClient = createApiClient(node, state.certs)
      val ns = state.copy(
        apiClient = apiClient,
        node = node
      )
      // TODO: notify dns proxy about ip changes
      updateStateSync(for {
        _ ← PNode.updatePublicIpAddress(nodeId, ipAddress)
        s ← checkAndUpdateState(ns)
      } yield s)(identity)
    }
  }

  def sendReplyByState(state: State, replyTo: ActorRef) =
    state.node.state match {
      case NodeState.Available ⇒
        replyTo.!(())
      case _ ⇒
        replyTo ! Status.Failure(NodeIsNotAvailable)
    }

  private val apiClientNotInitialized =
    Future.successful(DockerApiClientIsNotInitialized.left)

  def apiCall[T](state: State)(f: Client ⇒ Future[T]): Future[Throwable \/ T] = {
    state.apiClient match {
      case Some(api) ⇒ f(api).map(_.right).recover {
        case e: Throwable ⇒
          log.error(s"API call error: ${e.getMessage}")
          e.left
      }
      case None ⇒
        log.error("Docker API client is not initialized")
        apiClientNotInitialized
    }
  }

  def checkAndUpdateState(state: State) = {
    val p = Promise[Unit]()
    system.scheduler.scheduleOnce(2.seconds)(p.complete(Success(())))
    for {
      _ ← p.future
      res ← apiCall(state)(_.ping) // TODO: add retry with exponential backoff
      ns = res match {
        case \/-(_) ⇒ NodeState.Available
        case -\/(_) ⇒ NodeState.Unreachable
      }
      _ ← PNode.updateState(state.node.getId, ns)
    } yield state.copy(node = state.node.copy(state = ns))
  }

  def createApiClient(node: MNode, certs: DockerApiCerts) =
    node.publicIpAddress.map { ip ⇒
      val sslContext = Tls.context(certs.key, certs.cert, Some(certs.ca))
      new Client(ip, 2375, Some(sslContext))
    }

  def imagePull(state: State, image: DockerImage) = {
    val expireTime = LocalDateTime.now().minusSeconds(pullingCacheTtl)
    val cache = state.images.pullingCache.filter(_._2._1.isAfter(expireTime))
    val tag = image.tag.getOrElse("latest")
    val key = s"${image.name}:$tag"
    cache.get(key) match {
      case Some((_, p)) ⇒
        val ns = state.copy(images = state.images.copy(
          pullingCache = cache
        ))
        (ns, p.future)
      case None ⇒
        val p = Promise[Unit]
        val ns = state.copy(images = state.images.copy(
          pullingCache = cache + ((key, (LocalDateTime.now, p)))
        ))
        // (apiCall(state) { c ⇒
        //   c.imagePull(image.name, image.tag).flatMap(_.runWith(Sink.lastOption))
        // }).onComplete {
        //   case Success(opt) ⇒ opt match {
        //     case \/-(Some(evt)) ⇒
        //       if (evt.isFailure) {
        //         val msg = evt.errorDetail.map(_.message)
        //           .orElse(evt.errorMessage)
        //           .getOrElse("Unknown docker error")
        //         p.failure(new Throwable(msg)) // TODO
        //       }
        //       else p.complete(Success(()))
        //     case \/-(None) ⇒
        //       p.failure(new Throwable("Unknown docker error")) // TODO
        //     case -\/(e) ⇒ p.failure(e)
        //   }
        //   case Failure(e) ⇒ p.failure(e)
        // }
        p.complete(Success(()))
        (ns, p.future)
    }
  }

  def containerCreate(
    state:         State,
    container:     MContainer,
    image:         DockerImage,
    deployOptions: DockerDeployOptions
  ) = {
    log.debug(s"createContainer: $container")
    val command = deployOptions.command.map(_.split(' ').toList)
      .getOrElse(List.empty)
    val config = ContainerMethods.ContainerCreate(
      image = image.name,
      command = command
    )
    val name = Some(container.dockerName)
    val (ns, pullFut) = imagePull(state, image)
    val resFut = for {
      _ ← pullFut
      \/-(res) ← apiCall(state)(_.containerCreate(config, name))
    } yield container.copy(
      state = ContainerState.Created,
      dockerId = Some(res.id)
    )
    (updateStateContainer(ns, container), resFut)
  }

  def containerStart(state: State, containerId: Long) = {
    log.debug("container start: $containerId")

    state.containers.get(containerId).flatMap(_.dockerId) match {
      case Some(dockerId) =>
        apiCall(state)(_.containerStart(dockerId))
        sender().!(())
      case _ => ???
    }
  }

  def containerStop(state: State, containerId: Long) = {
    // TODO: DRY
    // containersMap.get(containerId).flatMap(_.dockerId) match {
    //   case Some(dockerId) ⇒
    //     state.apiClient.containerStop(dockerId, 30.seconds)
    //       .pipeTo(sender())
    //       .onComplete(println)
    //   case None ⇒ ???
    // }
  }

  def containerRestart(state: State, containerId: Long) = {
    // TODO: DRY
    // containersMap.get(containerId).flatMap(_.dockerId) match {
    //   case Some(dockerId) ⇒
    //     (for {
    //       _ ← state.apiClient.containerStop(dockerId, 30.seconds)
    //       _ ← state.apiClient.containerStart(dockerId)
    //     } yield ())
    //       .pipeTo(sender())
    //       .onComplete(println)
    //   case None ⇒ ???
    // }
  }

  def containerTerminate(state: State, containerId: Long) = {
    // TODO: DRY
    println(s"terminateContainer: $containerId")
    //   containersMap.get(containerId).flatMap(_.dockerId) match {
    //     case Some(dockerId) ⇒
    //       println(s"dockerId: $dockerId")
    //       state.apiClient.containerRemove(dockerId, true, true) // TODO: volumes
    //         .pipeTo(sender())
    //         .onComplete(println)
    //     case None ⇒ ???
    //   }
  }
}
