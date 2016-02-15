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
import io.fcomb.docker.api._, methods.{ContainerMethods, ResouceOrContainerNotFoundException}
import akka.actor._
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.cluster.sharding._
import akka.pattern.{after, ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.collection.immutable.{HashSet, HashMap, LongMap}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import scalaz._, Scalaz._
import java.time.{LocalDateTime, ZonedDateTime}
import java.net.InetAddress
import javax.net.ssl.SSLContext

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

  case object NodeTerminate extends Entity

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

  def terminate(nodeId: Long)(
    implicit
    ec:      ExecutionContext,
    timeout: Timeout          = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](nodeId, NodeTerminate, timeout)

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
  case class State(nodeState: NodeState.NodeState)

  private[node] sealed trait NodeProcessorMessage

  private[node] case object DockerPing extends NodeProcessorMessage

  private[node] case object Unreachable extends NodeProcessorMessage

  private[node] case object Available extends NodeProcessorMessage

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

  private var apiClient: Option[Client] = None
  private var publicIpAddress: Option[InetAddress] = None
  private var sslContext: Option[SSLContext] = None
  private var containers: LongMap[MContainer] = LongMap.empty
  private var imagesPullingCache: HashMap[String, (LocalDateTime, Promise[Unit])] = HashMap.empty
  private var lastPingAt: Long = 0L

  val nodeId = self.path.name.toLong
  // TODO: val dockerApiTimeout = 1.minute
  val pingInterval = 1.second // (25 + Random.random.nextInt(15)).seconds
  val pingIntervalMillis = pingInterval.toMillis

  val pullingCacheTtl = 5.minutes.toSeconds // TODO: drop TTL and check images locally or remote

  system.scheduler.schedule(pingInterval, pingInterval)(self ! DockerPing)

  def getInitialState = State(NodeState.Pending)

  def initializing() = {
    becomeStashing()
    PNode.findByPk(nodeId).onComplete {
      case Success(res) ⇒ res match {
        case Some(node) ⇒
          node.state match {
            case NodeState.Terminated | NodeState.Terminating ⇒
              modifyState(_.copy(nodeState = node.state))
              unstashReceive()
            case _ ⇒ (for {
              Some((rootCert, clientCert)) ← UserCertificate
                .findRootAndClientCertsByUserId(node.userId)
              containers ← PContainer.findAllByNodeId(nodeId)
            } yield {
              this.sslContext = Some(Tls.context(
                clientCert.key,
                clientCert.certificate,
                Some(rootCert.certificate)
              ))
              node.publicIpInetAddress().foreach { ip ⇒
                this.publicIpAddress = Some(ip)
                createApiClient(ip)
              }
              this.containers = wrapContainers(containers)
              modifyState(_.copy(nodeState = node.state))
              unstashReceive()
            }).onFailure(handleFailure)
          }
        case None ⇒ annihilation()
      }
      case Failure(e) ⇒ handleThrowable(e)
    }
  }

  initializing()

  def receive = receiveByState(getState)

  def receiveByState(state: State): Receive =
    state.nodeState match {
      case NodeState.Available   ⇒ availableReceive
      case NodeState.Pending     ⇒ pendingReceive
      case NodeState.Unreachable ⇒ unreachableReceive
      case NodeState.Terminated  ⇒ terminatedReceive
      case NodeState.Terminating ⇒
        log.warning("terminating node by state")
        terminate()
        stashReceive
    }

  val availableReceive: Receive = {
    case msg: Entity ⇒
      msg match {
        case NodeRegister(ip) ⇒ nodeRegister(ip)
        // case ContainerCreate(container, image, deployOptions) ⇒
        //   containerCreate(state, container, image, deployOptions, replyTo)
        // case ContainerStart(containerId) ⇒
        //   containerStart(state, containerId, replyTo)
        // case ContainerStop(containerId) ⇒
        //   containerStop(state, containerId, replyTo)
        // case ContainerRestart(containerId) ⇒
        //   containerRestart(state, containerId, replyTo)
        // case ContainerTerminate(containerId) ⇒
        //   containerTerminate(state, containerId, replyTo)
      }
    case cmd: NodeProcessorMessage ⇒ cmd match {
      case DockerPing ⇒ nodePing()
      case Unreachable ⇒
        modifyContainersState(ContainerState.Unreachable)
        val futState = updateNodeState(getState, NodeState.Unreachable)
        putStateWithReceiveAsync(futState)(_ ⇒ unreachableReceive)
      case Available                  ⇒
      case UpdateContainer(container) ⇒
      // TODO: remove terminated container from state `containers`
      // updateStateContainer(container)
    }
    case ReceiveTimeout ⇒
      if (this.containers.isEmpty) annihilation()
  }

  val pendingReceive: Receive = {
    case msg: Entity ⇒
      val replyTo = sender()
      msg match {
        case NodeRegister(ip) ⇒ nodeRegister(ip)
        case _                ⇒ replyTo ! Status.Failure(NodeIsNotAvailable)
      }
    case _: NodeProcessorMessage ⇒
    case ReceiveTimeout ⇒
      if (this.containers.isEmpty) annihilation()
  }

  val unreachableReceive: Receive = {
    case cmd: NodeProcessorMessage ⇒ cmd match {
      case DockerPing  ⇒ nodePing()
      case Unreachable ⇒
      case Available ⇒
        // TODO: update containers state
        val futState = updateNodeState(getState, NodeState.Available)
        putStateWithReceiveAsync(futState)(_ ⇒ unreachableReceive)
      case UpdateContainer(container) ⇒
      // TODO
    }
  }

  val terminatedReceive: Receive = {
    case _ ⇒
  }

  def nodePing() = {
    val currentTime = System.currentTimeMillis
    if (this.lastPingAt + pingIntervalMillis >= currentTime) {
      this.lastPingAt = currentTime
      apiCall(_.ping()).foreach {
        case \/-(_) ⇒ self ! Available
        case -\/(_) ⇒ self ! Unreachable
      }
    }
  }

  def modifyContainersState(state: ContainerState.ContainerState) = {
    this.containers = this.containers.map {
      case (id, c) ⇒
        val updatedContainer = c.copy(state = state)
        ApplicationProcessor.containerChangedState(updatedContainer)
        (id, updatedContainer)
    }
  }

  def terminate() = {
    // TODO: cancel all logs and attachments
    val futState =
      updateNodeState(getState, NodeState.Terminating).flatMap { s ⇒
        modifyContainersState(ContainerState.Terminated)
        this.imagesPullingCache.values.foreach {
          case (_, p) if !p.isCompleted ⇒ p.failure(NodeIsNotAvailable)
        }
        this.containers = LongMap.empty
        this.imagesPullingCache = HashMap.empty
        this.apiClient = None
        this.sslContext = None
        updateNodeState(s, NodeState.Terminated)
      }
    putStateWithReceiveAsync(futState)(receiveByState)
  }

  def checkNodeAndUpdateState(state: State) = {
    val p = Promise[Unit]()
    system.scheduler.scheduleOnce(2.seconds)(p.complete(Success(())))
    for {
      _ ← p.future
      res ← apiCall(_.ping) // TODO: add retry with exponential backoff
      nodeState = res match {
        case \/-(_) ⇒ NodeState.Available
        case -\/(_) ⇒ NodeState.Unreachable
      }
      newState ← updateNodeState(state, nodeState)
    } yield newState
  }

  def nodeRegister(ip: InetAddress) = {
    val replyTo = sender()
    log.info(s"register $ip")
    val state = getState
    val futState =
      if (this.publicIpAddress.contains(ip))
        checkNodeAndUpdateState(state)
      else {
        val ipAddress = ip.getHostAddress
        this.publicIpAddress = Some(ip)
        createApiClient(ip)
        // TODO: notify dns proxy about ip changes
        for {
          _ ← PNode.updatePublicIpAddress(nodeId, ipAddress)
          s ← checkNodeAndUpdateState(state)
        } yield s
      }
    putStateWithReceiveAsync(futState)(receiveByState)
      .map(sendReplyByState(_, NodeState.Available, replyTo))
  }

  def syncContainers() = {
    val containersMap = this.containers.values.collect {
      case c if c.dockerId.nonEmpty ⇒ (c.dockerId.get, c.getId)
    }.toMap
    for {
      \/-(items) ← apiCall(_.containers(showAll = true))
      dropIds = items.foldLeft(List.empty[String]) {
        case (ids, item) ⇒
          containersMap.get(item.id) match {
            case Some(containerId) ⇒
              val cs = ContainerState.parseDockerStatus(item.status)
              modifyContainer(containerId)(_.copy(state = cs))
              ids
            case None ⇒ item.id :: ids
          }
      }
      _ ← dropIds.foldLeft(Future.successful(().right[Throwable])) {
        case (f, id) ⇒ f.flatMap { _ ⇒
          apiCall(_.containerRemove(id, withForce = true, withVolumes = true))
        }
      }
    } yield ()
  }

  def sendReplyByState(state: State, nodeState: NodeState.NodeState, replyTo: ActorRef) =
    state.nodeState match {
      case `nodeState` ⇒ replyTo.!(())
      case _           ⇒ replyTo ! Status.Failure(NodeIsNotAvailable)
    }

  def updateNodeState(state: State, nodeState: NodeState.NodeState) = {
    PNode.updateState(nodeId, nodeState).map { _ ⇒
      state.copy(nodeState = nodeState)
    }
  }

  private val apiClientNotInitialized =
    Future.successful(DockerApiClientIsNotInitialized.left)

  def apiCall[T](f: Client ⇒ Future[T]): Future[Throwable \/ T] = {
    this.apiClient match {
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

  def createApiClient(ip: InetAddress) =
    this.apiClient = Some(new Client(ip.getHostAddress, 2375, this.sslContext))

  def imagePull(image: DockerImage) = {
    val expireTime = LocalDateTime.now().minusSeconds(pullingCacheTtl)
    val cache = this.imagesPullingCache.filter(_._2._1.isAfter(expireTime))
    val tag = image.tag.getOrElse("latest")
    val key = s"${image.name}:$tag"
    cache.get(key) match {
      case Some((_, p)) ⇒
        this.imagesPullingCache = cache
        p.future
      case None ⇒
        val p = Promise[Unit]
        this.imagesPullingCache = cache + ((key, (LocalDateTime.now, p)))
        (apiCall { c ⇒
          c.imagePull(image.name, image.tag).flatMap(_.runWith(Sink.lastOption))
        }).onComplete {
          case Success(opt) ⇒ opt match {
            case \/-(Some(evt)) ⇒
              if (evt.isFailure) {
                val msg = evt.errorDetail.map(_.message)
                  .orElse(evt.errorMessage)
                  .getOrElse("Unknown docker error")
                p.failure(new Throwable(msg)) // TODO
              }
              else p.complete(Success(()))
            case \/-(None) ⇒
              p.failure(new Throwable("Unknown docker error")) // TODO
            case -\/(e) ⇒ p.failure(e)
          }
          case Failure(e) ⇒ p.failure(e)
        }
        p.future
    }
  }

  def containerCreate(
    state:         State,
    container:     MContainer,
    image:         DockerImage,
    deployOptions: DockerDeployOptions,
    replyTo:       ActorRef
  ) = {
    log.debug(s"createContainer: $container")
    val command = deployOptions.command.map(_.split(' ').toList)
      .getOrElse(List.empty)
    val config = ContainerMethods.ContainerCreate(
      image = image.name,
      command = command
    )
    val name = Some(container.dockerName)
    (for {
      _ ← imagePull(image)
      \/-(res) ← apiCall(_.containerCreate(config, name))
    } yield container.copy(
      state = ContainerState.Created,
      dockerId = Some(res.id)
    )).onComplete {
      case Success(c) ⇒
        self ! UpdateContainer(c)
        replyTo ! c
      case Failure(e) ⇒
        val c = container.copy(state = ContainerState.Terminated)
        self ! UpdateContainer(c)
        replyTo ! c
    }
    modifyContainers(_ + ((container.getId, container)))
  }

  def modifyContainer(id: Long)(f: MContainer ⇒ MContainer) = {
    this.containers.get(id).foreach { c ⇒
      this.containers = this.containers + (c.getId, f(c))
    }
  }

  def modifyContainers(f: LongMap[MContainer] ⇒ LongMap[MContainer]) =
    this.containers = f(this.containers)

  def containerStart(state: State, containerId: Long, replyTo: ActorRef) = {
    log.debug(s"container start: $containerId")
    applyStateToContainer(state, containerId, ContainerState.Running, replyTo) {
      (client, dockerId) ⇒
        client.containerStart(dockerId)
    }
    // TODO: update state to starting
  }

  def containerStop(state: State, containerId: Long, replyTo: ActorRef) = {
    log.debug(s"container stop: $containerId")
    applyStateToContainer(state, containerId, ContainerState.Stopped, replyTo) {
      (client, dockerId) ⇒
        client.containerStop(dockerId, 30.seconds)
    }
  }

  def containerRestart(state: State, containerId: Long, replyTo: ActorRef) = {
    log.debug(s"container restart: $containerId")
    applyStateToContainer(state, containerId, ContainerState.Running, replyTo) {
      (client, dockerId) ⇒
        for {
          _ ← client.containerStop(dockerId, 30.seconds)
          _ ← client.containerStart(dockerId)
        } yield ()
    }
  }

  def containerTerminate(state: State, containerId: Long, replyTo: ActorRef) = {
    log.debug(s"container terminate: $containerId")
    applyStateToContainer(state, containerId, ContainerState.Terminated, replyTo) {
      (client, dockerId) ⇒
        client.containerRemove(dockerId, withForce = true, withVolumes = true)
    }
  }

  def applyStateToContainer(
    state:          State,
    containerId:    Long,
    containerState: ContainerState.ContainerState,
    replyTo:        ActorRef
  )(
    f: (Client, String) ⇒ Future[Unit]
  ) = {
    this.containers.get(containerId) match {
      case Some(c) ⇒
        def updateContainerStateAndReply() = {
          self ! UpdateContainer(c.copy(state = containerState))
          replyTo.!(())
        }

        if (c.state == state) replyTo.!(())
        else c.dockerId match {
          case Some(dockerId) ⇒
            apiCall(f(_, dockerId)).foreach {
              case \/-(_) ⇒ updateContainerStateAndReply()
              case -\/(e) ⇒
                if (containerState == ContainerState.Terminated)
                  updateContainerStateAndReply()
                else e match {
                  case _: ResouceOrContainerNotFoundException ⇒
                    self ! UpdateContainer(c.copy(state = ContainerState.Terminated))
                    replyTo ! Status.Failure(ContainerNotFoundOrTerminated)
                  case ex ⇒
                    replyTo ! Status.Failure(ex)
                }
            }
          case None ⇒
            replyTo ! Status.Failure(ContainerDockerIdCantBeEmpty)
        }
      case None ⇒
        replyTo ! Status.Failure(ContainerNotFoundOrTerminated)
    }
  }

  def wrapContainers(containers: Seq[MContainer]) =
    LongMap(containers.map(c ⇒ (c.getId, c)): _*)
}
