package io.fcomb.services.node

import io.fcomb.services.Exceptions._
import io.fcomb.services.UserCertificateProcessor
import io.fcomb.services.application.ApplicationProcessor
import io.fcomb.models.application.{Application ⇒ MApplication}
import io.fcomb.persist.docker.{Container ⇒ PContainer}
import io.fcomb.models.docker.ContainerState
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
import scala.concurrent.{Future, Promise}
import scala.collection.mutable.HashSet
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

  sealed trait Entity

  case class RegisterNode(ip: InetAddress) extends Entity

  case class CreateContainer(app: MApplication) extends Entity

  def props(timeout: Duration)(implicit mat: Materializer) =
    Props(new NodeProcessor(timeout))

  case class EntityEnvelope(nodeId: Long, payload: Entity)

  private var actorRef: ActorRef = _

  def register(nodeId: Long, ip: InetAddress)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[Unit] = {
    Option(actorRef) match {
      case Some(ref) ⇒
        ask(ref, EntityEnvelope(nodeId, RegisterNode(ip)))
          .mapTo[Unit]
      case None ⇒ Future.failed(EmptyActorRefException)
    }
  }

  def createContainer(nodeId: Long, app: MApplication)(
    implicit
    timeout: Timeout = Timeout(30.seconds)
  ): Future[PContainer.ValidationModel] = {
    // TODO: DRY
    Option(actorRef) match {
      case Some(ref) ⇒
        ask(ref, EntityEnvelope(nodeId, CreateContainer(app)))
          .mapTo[PContainer.ValidationModel]
      case None ⇒ Future.failed(EmptyActorRefException)
    }
  }
}

private[this] object NodeProcessorMessages {
  sealed trait DockerApiCommands

  case object DockerPing extends DockerApiCommands
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

  case class Initialize(node: MNode, certs: DockerApiCerts)

  case class State(
    apiClient: Client,
    node:      MNode,
    certs:     DockerApiCerts
  )

  case object Stop

  case class Failed(e: Throwable)

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
    case Initialize(node, certs) ⇒
      val apiClient = createApiClient(node, certs)
      val state = State(apiClient, node, certs)
      context.become(initialized(state), false)
      unstashAll()
    case msg: Entity ⇒
      log.warning(s"stash message: $msg")
      stash()
  }

  def initializing(ip: InetAddress) =
    loadNodeAndCerts.onSuccess {
      case (node, certs) ⇒
        if (node.publicIpInetAddress().exists(_ == ip))
          self ! Initialize(node, certs)
        else {
          val ipAddress = ip.getHostAddress
          PNode.updatePublicIpAddress(nodeId, ipAddress).onComplete {
            case Success(_) ⇒
              val nn = node.copy(publicIpAddress = Some(ipAddress))
              self ! Initialize(nn, certs)
            case Failure(e) ⇒ handleThrowable(e)
          }
        }
    }

  def initializing() =
    loadNodeAndCerts.onSuccess {
      case (node, certs) ⇒
        self ! Initialize(node, certs)
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
    }
    case cmd: DockerApiCommands ⇒ cmd match {
      case DockerPing ⇒
        state.apiClient.ping().onComplete(println)
    }
    case ReceiveTimeout ⇒ annihilation()
  }

  def loadNodeAndCerts(): Future[(MNode, DockerApiCerts)] = {
    val f = for {
      // TODO: add OptionT
      Some(node) ← PNode.findByPk(nodeId)
      Some((rootCert, clientCert)) ← UserCertificate
        .findRootAndClientCertsByUserId(node.userId)
    } yield {
      val certs = DockerApiCerts(
        clientCert.key,
        clientCert.certificate,
        rootCert.certificate
      )
      (node, certs)
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
      // TODO: parse image `tag`
      // TODO: cache this slow action
      // _ ← state.apiClient.imagePull(app.image.name, Some("latest"))
      //   .flatMap(_.runForeach(println)) // TODO: handle result through fold and return Future
      _ ← state.apiClient.containerCreate(config, Some(container.dockerName))
      _ ← PContainer.updateState(container.getId, ContainerState.Starting)
      _ ← state.apiClient.containerStart(container.dockerName)
      _ ← PContainer.updateState(container.getId, ContainerState.Running)
    } yield {
      val cc = container.copy(state = ContainerState.Running)
      ApplicationProcessor.newContainerState(
        cc.applicationId,
        cc.getId,
        cc.state
      )
      cc
    }).onComplete(println)
  }

  def failed(e: Throwable): Receive = {
    case _: Entity ⇒ sender ! Status.Failure(e)
    case Stop      ⇒ annihilation()
  }

  def handleThrowable(e: Throwable): Unit = {
    log.error(e, e.getMessage())
    context.become({
      case Failed(e) ⇒
        context.become(failed(e), false)
        unstashAll()
        self ! Stop
    }, false)
    self ! Failed(e)
  }

  def annihilation() = {
    log.info("annihilation!")
    context.parent ! Passivate(stopMessage = PoisonPill)
  }
}
