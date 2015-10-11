package io.fcomb.models.comb

import io.fcomb.models.ModelWithAutoLongPk
import java.time.LocalDateTime
import java.util.UUID
import java.net.URL
import io.fcomb.trie.RouteMethods

object MethodKind extends Enumeration {
  type MethodKind = Value

  val GET = Value("GET")
  val POST = Value("POST")
  val PUT = Value("PUT")
  val PATCH = Value("PATCH")
  val DELETE = Value("DELETE")
  val HEAD = Value("HEAD")
  val OPTIONS = Value("OPTIONS")
}

object CombMethodUtils {
  val endpointParameter = """\{([\w\-]+|\*)\}""".r

  def endpointParams(url: URL) =
    List(url.getPath, url.getQuery)
      .filterNot(_ == null)
      .foldLeft(List.empty[String]) { (acc, s) =>
        acc ::: endpointParameter.findAllIn(s).toList
      }
      .map(_.drop(1).dropRight(1))
}

@SerialVersionUID(1L)
case class CombMethod(
  id: Option[Long] = None,
  combId: Long,
  kind: MethodKind.MethodKind,
  uri: String,
  endpoint: String,
  createdAt: LocalDateTime,
  updatedAt: LocalDateTime
) extends ModelWithAutoLongPk {
  def endpointUrl() = new URL(endpoint)

  def endpointHost() = endpointUrl.getHost

  def endpointPort() = {
    val port = endpointUrl.getPort()
    if (port == -1) {
      if (endpointUrl.getProtocol == "https") 443
      else 80
    } else port
  }

  def endpointParams() =
    CombMethodUtils.endpointParams(endpointUrl)

  def withPk(id: Long) = this.copy(id = Some(id))

  def routeKind() = this.kind match {
    case MethodKind.GET => RouteMethods.GET
    case MethodKind.POST => RouteMethods.POST
    case MethodKind.PUT => RouteMethods.PUT
    case MethodKind.DELETE => RouteMethods.DELETE
    case MethodKind.PATCH => RouteMethods.PATCH
    case MethodKind.OPTIONS => RouteMethods.OPTIONS
    case MethodKind.HEAD => RouteMethods.HEAD
  }
}
