package io.fcomb.services.docker

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.Http
import akka.util.ByteString
import spray.json._
import spray.json.DefaultJsonProtocol._
import org.slf4j.LoggerFactory

class DockerApiClient(host: String, port: Int)(implicit sys: ActorSystem, mat: Materializer) {
  import sys.dispatcher
  import DockerApiMessages._
  import DockerApiMessages.JsonProtocols._

  private val logger = LoggerFactory.getLogger(this.getClass)

  lazy val connectionFlow =
    Http().outgoingConnection(host, port) // TODO: add TLS

  private def apiRequest[T <: DockerApiRequest](
    method: HttpMethod,
    uri: Uri,
    body: Option[T] = None
  )(implicit jw: JsonWriter[T]) = {
    val entity = body match {
      case Some(b) =>
        val req = b.toJson.compactPrint
        logger.debug(s"Docker API request: $req")
        HttpEntity(`application/json`, req)
      case _ => HttpEntity.Empty
    }
    Source
      .single(HttpRequest(uri = uri, method = method, entity = entity))
      .via(connectionFlow)
      .runWith(Sink.head)
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { res =>
        val s = res.utf8String
        logger.debug(s"Docker API response: $s")
        s.parseJson
      }
  }

  def getInfo() =
    apiRequest(HttpMethods.GET, "/info").map(_.convertTo[Info])

  def getVersion() =
    apiRequest(HttpMethods.GET, "/version").map(_.convertTo[Version])

  def getContainers(
    all: Boolean = true,
    size: Option[Int] = None,
    before: Option[String] = None
  ) = {
    val params = Map(
      "all" -> all.toString,
      "size" -> size.getOrElse("").toString,
      "before" -> before.getOrElse("")
    ).filter(_._2.nonEmpty)
    val uri = Uri("/containers/json").withQuery(params)
    apiRequest(HttpMethods.GET, uri).map { json =>
      println(s"containers: ${json.convertTo[List[ContainerItem]]}")
    }
  }

  def createContainer(image: String) = {
    val restartPolicy = RestartPolicy(
      name = None,
      maximumRetryCount = 0
    )
    val hostConfig = HostConfig(
      binds = List.empty,
      links = List.empty,
      lxcConf = Map.empty,
      memory = 0,
      memorySwap = 0,
      cpuShares = 512,
      cpuPeriod = 100000,
      cpusetCpus = "0,1",
      cpusetMems = "0,1",
      blockIoWeight = 300,
      memorySwappiness = 60,
      isOomKillDisable = false,
      portBindings = Map.empty,
      isPublishAllPorts = false,
      isPrivileged = false,
      isReadonlyRootfs = false,
      dns = List.empty,
      dnsSearch = List.empty,
      extraHosts = None,
      volumesFrom = List.empty,
      capacityAdd = List.empty,
      capacityDrop = List.empty,
      restartPolicy = restartPolicy,
      networkMode = "bridge",
      devices = List.empty,
      // Ulimits,
      // LogConfig,
      // SecurityOpt,
      cgroupParent = Option.empty
    )
    val req = ContainerCreate(
      hostname = None,
      domainName = None,
      user = None,
      isAttachStdin = false,
      isAttachStdout = false,
      isAttachStderr = false,
      isTty = false,
      isOpenStdin = false,
      isStdinOnce = false,
      env = List.empty,
      command = List.empty,
      entrypoint = None,
      image = image,
      labels = Map.empty,
      mounts = List.empty,
      isNetworkDisabled = false,
      workingDirectory = None,
      macAddress = None,
      hostConfig = hostConfig
    )
    println(s"req: $req")
    apiRequest(HttpMethods.POST, "/containers/create", Some(req)).map { json =>
      println(s"resp: ${json.convertTo[ContainerCreateResponse]}")
    }
  }

  def getContainer() = {
  }
}
