package io.fcomb.services.node

import io.fcomb.services.Exceptions._
import io.fcomb.services.UserCertificateProcessor
import io.fcomb.services.application.ApplicationProcessor
import io.fcomb.models.application.{Application ⇒ MApplication}
import io.fcomb.persist.docker.{Container ⇒ PContainer}
import io.fcomb.models.docker.{ContainerState, Container ⇒ MContainer}
import io.fcomb.models.node.{NodeState, Node ⇒ MNode}
import io.fcomb.utils.{Config, Implicits, Random}
import io.fcomb.crypto.{Certificate, Tls}
import io.fcomb.persist.node.{Node ⇒ PNode}
import io.fcomb.persist.UserCertificate
import io.fcomb.docker.api._, methods.ContainerMethods._
import akka.actor._
import akka.stream.Materializer
import akka.cluster.sharding._
import akka.pattern.{after, ask, pipe}
import akka.util.Timeout
import scala.concurrent.{Future, Promise, ExecutionContext}
import scala.collection.mutable.{HashSet, LongMap}
import scala.concurrent.duration._
import scala.util.{Success, Failure}
import java.time.ZonedDateTime
import java.net.InetAddress

object NodeProcessor {
  val extractEntityId: ShardRegion.ExtractEntityId = {
    case EntityEnvelope(nodeId, payload) ⇒ (nodeId.toString, payload)
  }

  val numberOfShards = 1

  val extractShardId: ShardRegion.ExtractShardId = {
    case EntityEnvelope(nodeId, _) ⇒ (nodeId % numberOfShards).toString
  }

  val shardName = "NodeProcessor"

  def startRegion(timeout: Duration)(
    implicit
    sys: ActorSystem,
    mat: Materializer
  ) = {
    val ref = ClusterSharding(sys).start(
      typeName = shardName,
      entityProps = props(timeout),
      settings = ClusterShardingSettings(sys),
      extractEntityId = extractEntityId,
      extractShardId = extractShardId
    )
    actorRef = ref
    ref
  }

  def initialize()(
    implicit
    ec: ExecutionContext
  ) =
    // TODO: only with avaialable state
    PNode.all().map(_.foreach { node ⇒
      actorRef ! EntityEnvelope(node.getId, WakeUp)
    })

  sealed trait Entity

  case object WakeUp extends Entity

  case class RegisterNode(ip: InetAddress) extends Entity

  case class CreateContainer(app: MApplication) extends Entity

  case class StartContainer(id: Long) extends Entity

  case class StopContainer(id: Long) extends Entity

  case class TerminateContainer(id: Long) extends Entity

  def props(timeout: Duration)(implicit mat: Materializer) =
    Props(new NodeProcessor(timeout))

  case class EntityEnvelope(nodeId: Long, payload: Entity)

  private var actorRef: ActorRef = _

  private def askRef[T](
    nodeId:  Long,
    entity:  Entity,
    timeout: Timeout
  )(
    implicit
    m: Manifest[T]
  ): Future[T] =
    Option(actorRef) match {
      case Some(ref) ⇒
        ask(ref, EntityEnvelope(nodeId, entity))(timeout).mapTo[T]
      case None ⇒ Future.failed(EmptyActorRefException)
    }

  private def tellRef(nodeId: Long, entity: Entity) =
    Option(actorRef).map(_ ! EntityEnvelope(nodeId, entity))

  def register(nodeId: Long, ip: InetAddress)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](nodeId, RegisterNode(ip), timeout)

  def createContainer(nodeId: Long, app: MApplication)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[PContainer.ValidationModel] =
    askRef(nodeId, CreateContainer(app), timeout)

  def startContainer(nodeId: Long, containerId: Long)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] =
    askRef[Unit](nodeId, StartContainer(containerId), timeout)

  def stopContainer(nodeId: Long, containerId: Long)(
    implicit
    timeout: Timeout = Timeout(1.minute)
  ): Future[Unit] =
    askRef[Unit](nodeId, StopContainer(containerId), timeout)
}

private[this] object NodeProcessorMessages {
  sealed trait NodeProcessorMessage

  case object DockerPing extends NodeProcessorMessage

  case class AppendContainer(container: MContainer) extends NodeProcessorMessage

  case class UpdateContainer(container: MContainer) extends NodeProcessorMessage
}

class NodeProcessor(timeout: Duration)(implicit mat: Materializer) extends Actor
    with Stash with ActorLogging {
  import context.dispatcher
  import context.system
  import NodeProcessor._
  import NodeProcessorMessages._
  import ShardRegion.Passivate

  context.setReceiveTimeout(timeout)

  val nodeId = self.path.name.toLong
  // TODO: val dockerApiTimeout = 1.minute
  val pingInterval = (25 + Random.random.nextInt(15)).seconds

  case class DockerApiCerts(key: Array[Byte], cert: Array[Byte], ca: Array[Byte])

  case class Initialize(
    node:       MNode,
    containers: Seq[MContainer],
    certs:      DockerApiCerts
  )

  case class State(
    apiClient: Client,
    node:      MNode,
    certs:     DockerApiCerts
  )

  case object Annihilation

  case class Failed(e: Throwable)

  // TODO: add container messages queue for sequential command applying
  private val containersMap = new LongMap[MContainer]()

  system.scheduler.schedule(pingInterval, pingInterval)(self ! DockerPing)

  def receive = {
    case msg: Entity ⇒
      stash()
      context.become(initializingRecieve, false)
      msg match {
        case RegisterNode(ip) ⇒
          initializing(ip)
        case _ ⇒
          initializing()
      }
  }

  val initializingRecieve: Receive = {
    case Initialize(node, containers, certs) ⇒
      val apiClient = createApiClient(node, certs)
      val state = State(apiClient, node, certs)
      containersMap ++= containers.map(c ⇒ (c.getId, c))
      context.become(initialized(state), false)
      unstashAll()
    case msg: Entity ⇒
      log.warning(s"stash message: $msg")
      stash()
  }

  def initializing(ip: InetAddress) =
    loadPersistData.onSuccess {
      case (node, containers, certs) ⇒
        if (node.publicIpInetAddress().exists(_ == ip))
          self ! Initialize(node, containers, certs)
        else {
          val ipAddress = ip.getHostAddress
          PNode.updatePublicIpAddress(nodeId, ipAddress).onComplete {
            case Success(_) ⇒
              val nn = node.copy(publicIpAddress = Some(ipAddress))
              self ! Initialize(nn, containers, certs)
            case Failure(e) ⇒ handleThrowable(e)
          }
        }
    }

  def initializing() =
    loadPersistData.onSuccess {
      case (node, containers, certs) ⇒
        self ! Initialize(node, containers, certs)
    }

  def initialized(state: State): Receive = {
    case msg: Entity ⇒ msg match {
      case RegisterNode(ip) ⇒
        if (state.node.publicIpInetAddress().exists(_ == ip)) {
          state.node.state match {
            case NodeState.Initializing ⇒
              checkToAvailableAndUpdateState(state)
            case NodeState.Available ⇒ sender.!(())
          }
        }
        else ??? // TODO: stash all messages and update IP address
      case CreateContainer(app) ⇒
        createContainer(state, app)
      case StartContainer(containerId) ⇒
        startContainer(state, containerId)
      case StopContainer(containerId) ⇒
        stopContainer(state, containerId)
      case TerminateContainer(containerId) ⇒
        terminateContainer(state, containerId)
      case WakeUp ⇒
        log.debug(s"awake node#$nodeId")
    }
    case cmd: NodeProcessorMessage ⇒ cmd match {
      case DockerPing ⇒
        state.apiClient.ping().onComplete(println)
      case AppendContainer(container) ⇒
        println(s"append container: $container")
        containersMap += (container.getId(), container)
      case UpdateContainer(container) =>
        println(s"update container: $container")
        containersMap += (container.getId(), container)
    }
    case ReceiveTimeout ⇒ annihilation()
  }

  def loadPersistData(): Future[(MNode, Seq[MContainer], DockerApiCerts)] = {
    val f = for {
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
      (node, containers, certs)
    }
    f.onFailure { case e ⇒ handleThrowable(e) }
    f
  }

  def checkToAvailableAndUpdateState(state: State) = {
    // TODO: DRY
    context.become({
      case st: State ⇒
        context.become(initialized(st), false)
        unstashAll()
      case _: Entity ⇒ stash()
    }, false)
    val replyTo = sender()
    // TODO: retry with fib timeout
    system.scheduler.scheduleOnce(2.seconds) {
      (for {
        res ← state.apiClient.ping // TODO: add retry with exponential backoff
        _ ← PNode.updateState(state.node.getId, NodeState.Available)
      } yield replyTo ! res).onComplete {
        case Success(_) ⇒
          self ! state.copy(node = state.node.copy(
            state = NodeState.Available
          ))
        case Failure(e) ⇒ handleThrowable(e)
      }
    }
  }

  def createApiClient(node: MNode, certs: DockerApiCerts) = {
    val sslContext = Tls.context(certs.key, certs.cert, Some(certs.ca))
    new Client(node.publicIpAddress.get, 2375, Some(sslContext))
  }

  def createContainer(state: State, app: MApplication) = {
    log.debug(s"createContainer: $app")
    val command = app.deployOptions.command.map(_.split(' ').toList)
      .getOrElse(List.empty)
    val config = ContainerCreate(
      image = app.image.name,
      command = command
    )
    (for {
      // TODO: handle Failure
      scalaz.Success(container) ← PContainer.create(
        userId = app.userId,
        applicationId = app.getId,
        nodeId = nodeId,
        name = s"${app.name}-1"
      )
      // TODO
      _ = self ! AppendContainer(container)
      _ = ApplicationProcessor.newContainer(container)

      // TODO: parse image `tag`
      // TODO: cache this slow action
      // _ ← state.apiClient.imagePull(app.image.name, Some("latest"))
      //   .flatMap(_.runForeach(println)) // TODO: handle result through fold and return Future
      res ← state.apiClient.containerCreate(config, Some(container.dockerName))
      sc ← updateContainerState(container, ContainerState.Starting, Some(res.id))

      _ ← state.apiClient.containerStart(sc.dockerName)
      rc ← updateContainerState(sc, ContainerState.Running)
    } yield rc).onComplete(println)
  }

  def updateContainerState(
    container: MContainer,
    state:     ContainerState.ContainerState,
    dockerId:  Option[String]                = None
  ): Future[MContainer] = {
    def sendUpdate(c: MContainer) = {
      self ! UpdateContainer(c)
      ApplicationProcessor.containerChangedState(c)
      c
    }

    val timeNow = ZonedDateTime.now
    if (dockerId.isEmpty)
      PContainer.updateState(container.getId, state, timeNow)
        .map { _ ⇒
          sendUpdate(container.copy(
            state = state,
            updatedAt = timeNow
          ))
        }
    else
      PContainer.updateStateAndDockerId(container.getId, state, dockerId, timeNow)
        .map { _ ⇒
          sendUpdate(container.copy(
            state = state,
            dockerId = dockerId,
            updatedAt = timeNow
          ))
        }
  }

  def startContainer(state: State, containerId: Long) = {
    // TODO: work with containers list
    containersMap.get(containerId).flatMap(_.dockerId) match {
      case Some(dockerId) ⇒
        state.apiClient.containerStart(dockerId)
          .pipeTo(sender())
          .onComplete(println)
      case None ⇒ ???
    }
  }

  def stopContainer(state: State, containerId: Long) = {
    // TODO: DRY
    containersMap.get(containerId).flatMap(_.dockerId) match {
      case Some(dockerId) ⇒
        state.apiClient.containerStop(dockerId, 30.seconds)
          .pipeTo(sender())
          .onComplete(println)
      case None ⇒ ???
    }
  }

  def terminateContainer(state: State, containerId: Long) = {
    // TODO: DRY
  }

  def failed(e: Throwable): Receive = {
    case _: Entity    ⇒ sender ! Status.Failure(e)
    case Annihilation ⇒ annihilation()
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
