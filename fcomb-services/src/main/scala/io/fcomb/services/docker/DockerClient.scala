package io.fcomb.services.docker

import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl._
import akka.http.scaladsl.model._
import akka.http.scaladsl.Http
import akka.util.ByteString
import spray.json._

object DockerApiMessages {
  sealed trait DockerApiMessage

  case class Info(
    id: String,
    continers: Int,
    images: Int,
    driver: String,
    driverStatus: List[List[String]],
    memoryLimit: Boolean,
    swapLimit: Boolean,
    cpuCfsPeriod: Boolean,
    cpuCfsQuota: Boolean,
    ipv4Forwarding: Boolean,
    kernelVersion: String,
    operatingSystem: String,
    memTotal: Long,
    name: String
  ) extends DockerApiMessage

  object JsonProtocols extends DefaultJsonProtocol {
    implicit val infoFormat = jsonFormat(Info, "ID", "Containers", "Images",
      "Driver", "DriverStatus", "MemoryLimit", "SwapLimit", "CpuCfsPeriod",
      "CpuCfsQuota", "IPv4Forwarding", "KernelVersion", "OperatingSystem",
      "MemTotal", "Name")
  }
}

class DockerClient(host: String, port: Int)(implicit sys: ActorSystem, mat: Materializer) {
  import sys.dispatcher
  import DockerApiMessages._
  import DockerApiMessages.JsonProtocols._

  lazy val connectionFlow =
    Http().outgoingConnection(host, port) // TODO: add TLS

  def getInfo() = {
    Source
      .single(HttpRequest(uri = "/info"))
      .via(connectionFlow)
      .runWith(Sink.head)
      .onComplete {
        case scala.util.Success(res) =>
          res.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map { data =>
            val json = data.utf8String.parseJson
            println(s"data: ${json.convertTo[Info]}")
          }
      }
  }
}
