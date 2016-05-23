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
import scala.collection.immutable
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import java.time.{LocalDateTime, ZonedDateTime}
import java.net.InetAddress
import javax.net.ssl.SSLContext
import akka.http.scaladsl.util.FastFuture
import cats.data.Xor

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
    PNode
      .findAllNonTerminated()
      .map(_.foreach { node ⇒
        tellRef(node.getId, WakeUp)
      })

  case object WakeUp

  case class NodeRegister(ip: InetAddress) extends Entity

  case object NodeTerminate extends Entity

  case class ContainerCreate(
    container:     MContainer,
    image:         DockerImage,
    deployOptions: DockerDeployOptions
  )
      extends Entity

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
    askRef[MContainer](
      container.nodeId,
      ContainerCreate(container, image, deployOptions),
      timeout
    )

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

  private[node] sealed trait ContainerAction

  private[node] case object ContainerStartAction extends ContainerAction

  private[node] case object ContainerStopAction extends ContainerAction

  private[node] case object ContainerRestartAction extends ContainerAction

  private[node] case object ContainerTerminateAction extends ContainerAction

  private[node] case class UpdateContainerState(
    containerId:    Long,
    containerState: ContainerState.ContainerState,
    updateEvent:    Boolean
  )
      extends NodeProcessorMessage

  private[node] case class UpdateContainerStateAndDockerId(
    containerId:    Long,
    containerState: ContainerState.ContainerState,
    dockerId:       Option[String]
  )
      extends NodeProcessorMessage
}

class NodeProcessor(timeout: Duration)(implicit mat: Materializer)
    extends Actor
    with Stash
    with ActorLogging
    with ProcessorActor[NodeProcessorMessages.State] {
  import context.dispatcher
  import context.system
  import NodeProcessor._
  import NodeProcessorMessages._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  private var apiClient: Option[Client] = None
  private var publicIpAddress: Option[InetAddress] = None
  private var sslContext: Option[SSLContext] = None
  private var containers: immutable.LongMap[MContainer] =
    immutable.LongMap.empty
  private var containersActionQueue: immutable.LongMap[immutable.Vector[(ContainerAction, ActorRef)]] = immutable.LongMap.empty
  private var imagesPullingCache: immutable.HashMap[String, (LocalDateTime, Promise[Unit])] = immutable.HashMap.empty
  private var lastPingAt: Long = 0L

  val nodeId = self.path.name.toLong
  // TODO: val dockerApiTimeout = 1.minute
  val pingInterval = 1.second // (25 + Random.random.nextInt(15)).seconds
  val pingIntervalMillis = pingInterval.toMillis

  val pullingCacheTtl =
    5.minutes.toSeconds // TODO: drop TTL and check images locally or remote

  val pingTimer =
    system.scheduler.schedule(pingInterval, pingInterval)(self ! DockerPing)

  def getInitialState = State(NodeState.Pending)

  def initializing() = {
    becomeStashing()
    PNode.findByPk(nodeId).onComplete {
      case Success(res) ⇒
        res match {
          case Some(node) ⇒
            node.state match {
              case NodeState.Terminated | NodeState.Terminating ⇒
                modifyState(_.copy(nodeState = node.state))
                unstashReceive()
              case _ ⇒
                (for {
                  Some((rootCert, clientCert)) ← UserCertificate
                    .findRootAndClientCertsByUserId(
                      node.userId
                    )
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
        case NodeRegister(ip) ⇒
          nodeRegister(ip)
        case ContainerCreate(container, image, deployOptions) ⇒
          containerCreate(getState, container, image, deployOptions)
        case ContainerStart(containerId) ⇒
          applyActionToContainer(
            getState, containerId, ContainerStartAction, sender()
          )
        case ContainerStop(containerId) ⇒
          applyActionToContainer(
            getState, containerId, ContainerStopAction, sender()
          )
        case ContainerRestart(containerId) ⇒
          applyActionToContainer(
            getState, containerId, ContainerRestartAction, sender()
          )
        case ContainerTerminate(containerId) ⇒
          applyActionToContainer(
            getState, containerId, ContainerTerminateAction, sender()
          )
      }
    case cmd: NodeProcessorMessage ⇒
      cmd match {
        case DockerPing ⇒
          nodePing()
        case Unreachable ⇒
          modifyContainersState(ContainerState.Unreachable)
          cleanContainersAction()
          val futState = updateNodeState(getState, NodeState.Unreachable)
          putStateWithReceiveAsync(futState)(_ ⇒ unreachableReceive)
        case UpdateContainerState(containerId, containerState, updateEvent) ⇒
          putContainerState(containerId, containerState, updateEvent)
          unqueueContainersAction(getState, containerId)
        case UpdateContainerStateAndDockerId(
          containerId, containerState, dockerId) ⇒
          putContainerStateAndDockerId(containerId, containerState, dockerId)
          unqueueContainersAction(getState, containerId)
        case _ ⇒
      }
    case ReceiveTimeout ⇒
      if (this.containers.isEmpty) annihilation()
  }

  val pendingReceive: Receive = {
    case msg: Entity ⇒
      msg match {
        case NodeRegister(ip) ⇒ nodeRegister(ip)
        case _                ⇒ sender() ! Status.Failure(NodeIsNotAvailable)
      }
    case _: NodeProcessorMessage ⇒
    case ReceiveTimeout ⇒
      if (this.containers.isEmpty) annihilation()
  }

  val unreachableReceive: Receive = {
    case msg: Entity ⇒
      msg match {
        case NodeRegister(ip) ⇒ nodeRegister(ip)
        case _                ⇒ sender() ! Status.Failure(NodeIsNotAvailable)
      }
    case cmd: NodeProcessorMessage ⇒
      cmd match {
        case DockerPing ⇒ nodePing()
        case Available ⇒
          val futState = for {
            _ ← syncContainers()
            state ← updateNodeState(getState, NodeState.Available)
          } yield state
          putStateWithReceiveAsync(futState)(_ ⇒ unreachableReceive)
        case _ ⇒
      }
    case ReceiveTimeout ⇒
      if (this.containers.isEmpty) annihilation()
  }

  val terminatedReceive: Receive = {
    case _: Entity ⇒ sender() ! Status.Failure(NodeIsTerminated)
    case cmd: NodeProcessorMessage ⇒
      cmd match {
        case DockerPing ⇒ pingTimer.cancel()
        case _          ⇒ sender() ! Status.Failure(NodeIsTerminated)
      }
    case ReceiveTimeout ⇒ annihilation()
  }

  def nodePing() = {
    val currentTime = System.currentTimeMillis
    if (this.lastPingAt + pingIntervalMillis >= currentTime) {
      this.lastPingAt = currentTime
      apiCall(_.ping()).foreach {
        case Xor.Right(_) ⇒ self ! Available
        case Xor.Left(_)  ⇒ self ! Unreachable
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
    val futState = updateNodeState(getState, NodeState.Terminating).flatMap {
      s ⇒
        modifyContainersState(ContainerState.Terminated)
        this.imagesPullingCache.values.foreach {
          case (_, p) if !p.isCompleted ⇒ p.failure(NodeIsNotAvailable)
        }
        this.containers = immutable.LongMap.empty
        this.imagesPullingCache = immutable.HashMap.empty
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
        case Xor.Right(_) ⇒ NodeState.Available
        case Xor.Left(_)  ⇒ NodeState.Unreachable
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
    apiCall(_.containers(showAll = true)).flatMap {
      case Xor.Right(items) ⇒
        val itemsMap = items.map { item ⇒
          (item.id, ContainerState.parseDockerStatus(item.status))
        }.toMap
        val dropIds = this.containers.foldLeft(List.empty[String]) {
          case (ids, (containerId, container)) ⇒
            container.dockerId match {
              case Some(dockerId) ⇒
                itemsMap.get(dockerId) match {
                  case Some(containerState) ⇒
                    putContainerState(
                      containerId, containerState, updateEvent = true
                    )
                    ids
                  case None ⇒ dockerId :: ids
                }
              case None ⇒
                putContainerState(
                  containerId, ContainerState.Terminated, updateEvent = true
                )
                ids
            }
        }
        dropIds.foldLeft(Future.successful(Xor.right[Throwable, Unit](()))) {
          case (f, id) ⇒
            f.flatMap { _ ⇒
              apiCall(
                _.containerRemove(id, withForce = true, withVolumes = true)
              )
            }
        }
      case Xor.Left(e) ⇒ FastFuture.failed(e)
    }
  }

  def sendReplyByState(
    state: State, nodeState: NodeState.NodeState, replyTo: ActorRef
  ) =
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
    FastFuture.successful(Xor.Left(DockerApiClientIsNotInitialized))

  def apiCall[T](f: Client ⇒ Future[T]): Future[Xor[Throwable, T]] = {
    this.apiClient match {
      case Some(api) ⇒
        f(api).map(Xor.Right(_)).recover {
          case e: Throwable ⇒
            log.error(s"API call error: ${e.getMessage}")
            Xor.Left(e)
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
          c.imagePull(image.name, image.tag)
            .flatMap(_.runWith(Sink.lastOption))
        }).onComplete {
          case Success(opt) ⇒
            opt match {
              case Xor.Right(Some(evt)) ⇒
                if (evt.isFailure) {
                  val msg = evt.errorDetail
                    .map(_.message)
                    .orElse(evt.errorMessage)
                    .getOrElse("Unknown docker error")
                  p.failure(new Throwable(msg)) // TODO
                }
                else p.complete(Success(()))
              case Xor.Right(None) ⇒
                p.failure(new Throwable("Unknown docker error")) // TODO
              case Xor.Left(e) ⇒ p.failure(e)
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
    deployOptions: DockerDeployOptions
  ) = {
    log.debug(s"createContainer: $container")
    val replyTo = sender()
    val command =
      deployOptions.command.map(_.split(' ').toList).getOrElse(List.empty)
    val config = ContainerMethods.ContainerCreate(
      image = image.name,
      command = command
    )
    val name = Some(container.dockerName)
    (for {
      _ ← imagePull(image)
      Xor.Right(res) ← apiCall(_.containerCreate(config, name))
    } yield container.copy(
      state = ContainerState.Created,
      dockerId = Some(res.id)
    )).onComplete {
      case Success(c) ⇒
        self ! UpdateContainerStateAndDockerId(c.getId, c.state, c.dockerId)
        replyTo ! c
      case Failure(e) ⇒
        val c = container.copy(state = ContainerState.Terminated)
        updateContainerState(c.getId, c.state, updateEvent = false)
        replyTo ! c
    }
    modifyContainers(_ + ((container.getId, container)))
  }

  def putContainerState(
    containerId:    Long,
    containerState: ContainerState.ContainerState,
    updateEvent:    Boolean
  ) =
    this.containers.get(containerId).foreach { c ⇒
      if (updateEvent)
        ApplicationProcessor.containerChangedState(
          c.copy(state = containerState)
        )
      if (containerState == ContainerState.Terminated)
        this.containers = this.containers - containerId
      else {
        val nc = c.copy(state = containerState)
        this.containers = this.containers + ((containerId, nc))
      }
    }

  def putContainerStateAndDockerId(
    containerId:    Long,
    containerState: ContainerState.ContainerState,
    dockerId:       Option[String]
  ) =
    this.containers.get(containerId).foreach { c ⇒
      val nc = c.copy(
        state = containerState,
        dockerId = dockerId
      )
      this.containers = this.containers + ((containerId, nc))
    }

  def modifyContainers(
    f: immutable.LongMap[MContainer] ⇒ immutable.LongMap[MContainer]
  ) =
    this.containers = f(this.containers)

  def applyActionToContainer(
    state:       State,
    containerId: Long,
    action:      ContainerAction,
    replyTo:     ActorRef
  ) = {
    log.debug(s"apply action $action to container $containerId")
    this.containers.get(containerId) match {
      case Some(container) ⇒
        container.dockerId match {
          case Some(dockerId) ⇒
            if (container.isInProgress)
              queueContainersAction(container, action, replyTo)
            else
              handleContainerAction(
                state, container, dockerId, action, replyTo
              )
          case None ⇒
            replyTo ! Status.Failure(ContainerDockerIdCantBeEmpty)
        }
      case None ⇒
        replyTo ! Status.Failure(ContainerNotFoundOrTerminated)
    }
  }

  private def queueContainersAction(
    container: MContainer,
    action:    ContainerAction,
    replyTo:   ActorRef
  ) = {
    val containerId = container.getId
    val vec = this.containersActionQueue.get(containerId) match {
      case Some(v) ⇒ v :+ ((action, replyTo))
      case None    ⇒ immutable.Vector((action, replyTo))
    }
    this.containersActionQueue =
      this.containersActionQueue + ((containerId, vec))
  }

  private def unqueueContainersAction(state: State, containerId: Long) = {
    if (this.containers.contains(containerId))
      this.containersActionQueue.get(containerId).foreach { vec ⇒
        this.containersActionQueue =
          this.containersActionQueue + ((containerId, vec.tail))
        val (action, actorRef) = vec.head
        applyActionToContainer(state, containerId, action, actorRef)
      }
    else {
      this.containersActionQueue
        .get(containerId)
        .foreach(_.foreach {
          case (_, replyTo) ⇒
            replyTo ! Status.Failure(ContainerNotFoundOrTerminated)
        })
      this.containersActionQueue = this.containersActionQueue - containerId
    }
  }

  private def cleanContainersAction() = {
    this.containersActionQueue.values.flatMap(_.map(_._2)).foreach { replyTo ⇒
      replyTo ! Status.Failure(NodeIsNotAvailable)
    }
    this.containersActionQueue = immutable.LongMap.empty
  }

  private def handleContainerAction(
    state:     State,
    container: MContainer,
    dockerId:  String,
    action:    ContainerAction,
    replyTo:   ActorRef
  ) = {
    val (startState, finalState) = action match {
      case ContainerStartAction ⇒
        (ContainerState.Starting, ContainerState.Running)
      case ContainerRestartAction ⇒
        (ContainerState.Stopping, ContainerState.Running)
      case ContainerStopAction ⇒
        (ContainerState.Stopping, ContainerState.Stopped)
      case ContainerTerminateAction ⇒
        (ContainerState.Terminating, ContainerState.Terminated)
    }
    val containerId = container.getId
    putContainerState(containerId, startState, updateEvent = false)
    containerApiCallByAction(action, dockerId).foreach {
      case Xor.Right(_) ⇒
        updateContainerState(containerId, finalState, updateEvent = false)
        replyTo.!(())
      case Xor.Left(e) ⇒
        if (action == ContainerTerminateAction) {
          updateContainerState(
            containerId, ContainerState.Terminated, updateEvent = false
          )
          replyTo.!(())
        }
        else
          e match {
            case _: ResouceOrContainerNotFoundException ⇒
              updateContainerState(
                containerId, ContainerState.Terminated, updateEvent = false
              )
              replyTo ! Status.Failure(ContainerNotFoundOrTerminated)
            case ex ⇒
              updateContainerState(
                containerId, ContainerState.Unreachable, updateEvent = false
              )
              replyTo ! Status.Failure(ex)
          }
    }
  }

  private def containerApiCallByAction(
    action: ContainerAction, dockerId: String
  ) =
    action match {
      case ContainerStartAction ⇒ apiCall(_.containerStart(dockerId))
      case ContainerStopAction  ⇒ apiCall(_.containerStop(dockerId, 30.seconds))
      case ContainerRestartAction ⇒
        apiCall { client ⇒
          for {
            _ ← client.containerStop(dockerId, 30.seconds)
            _ ← client.containerStart(dockerId)
          } yield ()
        }
      case ContainerTerminateAction ⇒
        apiCall(
          _.containerRemove(dockerId, withForce = true, withVolumes = true)
        )
    }

  def updateContainerState(
    containerId:    Long,
    containerState: ContainerState.ContainerState,
    updateEvent:    Boolean
  ) =
    self ! UpdateContainerState(containerId, containerState, updateEvent)

  private def wrapContainers(containers: Seq[MContainer]) =
    immutable.LongMap(containers.map(c ⇒ (c.getId, c)): _*)
}
