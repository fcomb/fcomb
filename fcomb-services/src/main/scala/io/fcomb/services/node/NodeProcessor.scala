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
import akka.cluster.sharding._
import akka.pattern.{after, ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.collection.immutable.HashSet
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scalaz._, Scalaz._
import java.time.ZonedDateTime
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
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](nodeId, NodeRegister(ip), timeout)

  def containerCreate(
    container:     MContainer,
    image:         DockerImage,
    deployOptions: DockerDeployOptions
  )(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[MContainer] =
    askRef[MContainer](container.nodeId, ContainerCreate(container, image, deployOptions), timeout)

  def containerStart(nodeId: Long, containerId: Long)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](nodeId, ContainerStart(containerId), timeout)

  def containerStop(nodeId: Long, containerId: Long)(
    implicit
    timeout: Timeout = Timeout(1.minute)
  ): Future[Unit] =
    askRef[Unit](nodeId, ContainerStop(containerId), timeout)

  def containerRestart(nodeId: Long, containerId: Long)(
    implicit
    timeout: Timeout = Timeout(1.minute)
  ): Future[Unit] =
    askRef[Unit](nodeId, ContainerRestart(containerId), timeout)

  def containerTerminate(nodeId: Long, containerId: Long)(
    implicit
    timeout: Timeout = Timeout(1.minute)
  ): Future[Unit] =
    askRef[Unit](nodeId, ContainerTerminate(containerId), timeout)
}

object NodeProcessorMessages {
  case class DockerApiCerts(
    key:  Array[Byte],
    cert: Array[Byte],
    ca:   Array[Byte]
  )

  case class State(
    apiClient:  Option[Client],
    node:       MNode,
    containers: HashSet[MContainer],
    certs:      DockerApiCerts
  )

  private[node] sealed trait NodeProcessorMessage

  private[node] case object DockerPing extends NodeProcessorMessage

  private[node] case class ContainerAppend(
    container: MContainer
  ) extends NodeProcessorMessage

  private[node] case class ContainerUpdate(
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
      initialize(State(
        apiClient, node, HashSet(containers: _*), certs
      ))
    }).onFailure {
      case e ⇒ handleThrowable(e)
    }
  }

  def stateReceive(state: State): Receive = {
    case msg: Entity ⇒ msg match {
      case NodeRegister(ip) ⇒ nodeRegister(state, ip)
      case ContainerCreate(container, image, deployOptions) ⇒
        println(s"CreateContainer($container, $image, $deployOptions)")
        val replyTo = sender()
        containerCreate(state, container, image, deployOptions).foreach { c ⇒
          self ! ContainerAppend(c)
          replyTo ! c
        }
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
      case ContainerAppend(container) ⇒
        println(s"append container: $container")
      // containersMap += (container.getId(), container)
      case ContainerUpdate(container) ⇒
        println(s"update container: $container")
      // containersMap += (container.getId(), container)
    }
    case ReceiveTimeout ⇒ annihilation()
  }

  def nodeRegister(state: State, ip: InetAddress) = {
    log.info(s"register $ip")
    if (state.node.publicIpInetAddress().contains(ip)) {
      state.node.state match {
        case NodeState.Pending ⇒
          updateStateSync(checkAndUpdateState(state))(identity)
        case NodeState.Available ⇒ sender.!(())
      }
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
      _ <- p.future
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
    for {
      // TODO: parse image `tag`
      // TODO: cache this slow action
      // _ ← state.apiClient.imagePull(app.image.name, Some("latest"))
      //   .flatMap(_.runForeach(println)) // TODO: handle result through fold and return Future
      \/-(res) ← apiCall(state)(_.containerCreate(config, Some(container.dockerName)))
    } yield container.copy(
      state = ContainerState.Created,
      dockerId = Some(res.id),
      nodeId = nodeId
    )
  }

  def containerStart(state: State, containerId: Long) = {
    // TODO: work with containers list
    // containersMap.get(containerId).flatMap(_.dockerId) match {
    //   case Some(dockerId) ⇒
    //     state.apiClient.containerStart(dockerId)
    //       .pipeTo(sender())
    //       .onComplete(println)
    //   case None ⇒ ???
    // }
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
