package io.fcomb.docker.api

import akka.actor.ActorSystem
import akka.http.HijackTcp
import akka.http.settings.clientSettingsWithIdleTimeout
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.{Http, ConnectionContext}
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.MediaTypes.`application/x-tar`
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl._
import akka.util.ByteString
import io.fcomb.docker.api.methods._
import javax.net.ssl.SSLContext
import org.slf4j.Logger
import scala.collection.immutable
import scala.concurrent.Future
import scala.concurrent.duration._
import spray.json._

private[api] trait ApiConnection {
  protected val logger: Logger

  val hostname: String
  val port: Int
  val sslContext: Option[SSLContext]

  implicit val sys: ActorSystem
  implicit val mat: Materializer

  import sys.dispatcher

  protected val clientConnectionSettings =
    ClientConnectionSettings(sys)

  private def httpConnectionFlow(duration: Option[Duration]) = {
    val settings = clientSettingsWithIdleTimeout(
      clientConnectionSettings,
      duration
    )
    val connection = sslContext match {
      case Some(ctx) ⇒
        Http().outgoingConnectionHttps(
          hostname,
          port,
          settings = settings,
          connectionContext = ConnectionContext.https(ctx)
        )
      case _ ⇒
        Http().outgoingConnection(hostname, port, settings = settings)
    }
    connection.buffer(10, OverflowStrategy.backpressure)
  }

  protected def hijackConnectionFlow(req: HttpRequest, idleTimeout: Duration) = {
    val settings = clientSettingsWithIdleTimeout(
      clientConnectionSettings,
      Some(idleTimeout)
    )
    HijackTcp.outgoingConnection(hostname, port, settings, req, responseFlow, sslContext)
  }

  protected def uriWithQuery(uri: Uri, queryParams: Map[String, String]) = {
    val q = uri.query() ++ queryParams.filter(_._2.nonEmpty)
    uri.withQuery(Uri.Query(q: _*))
  }

  protected def mapHttpResponse(res: HttpResponse) = {
    if (res.status.isSuccess()) Future.successful(res)
    else {
      res.entity.dataBytes.runFold(new StringBuffer) { (acc, bs) ⇒
        acc.append(bs.utf8String)
      }.map { buf ⇒
        val msg = buf.toString()
        res.status.intValue() match {
          case 400 ⇒ throw new BadParameterException(msg)
          case 403 ⇒ throw new PermissionDeniedException(msg)
          case 404 ⇒ throw new ResouceOrContainerNotFoundException(msg)
          case 406 ⇒ throw new ImpossibleToAttachException(msg)
          case 409 ⇒ throw new ConflictException(msg)
          case 500 ⇒ throw new ServerErrorException(msg)
          case _   ⇒ throw new UnknownException(msg)
        }
      }
    }
  }

  protected val responseFlow =
    Flow[HttpResponse].mapAsync(1)(mapHttpResponse)

  protected def apiRequestAsSource(
    method:      HttpMethod,
    uri:         Uri,
    queryParams: Map[String, String]       = Map.empty,
    entity:      RequestEntity             = HttpEntity.Empty,
    headers:     immutable.Seq[HttpHeader] = immutable.Seq.empty,
    idleTimeout: Option[Duration]          = None
  ) = {
    Source
      .single(HttpRequest(
        uri = uriWithQuery(uri, queryParams),
        method = method,
        entity = entity,
        headers = headers
      ))
      .via(httpConnectionFlow(idleTimeout))
      .runWith(Sink.head)
      .flatMap(mapHttpResponse)
  }

  protected def requestJsonEntity[T](body: T)(
    implicit
    jw: JsonWriter[T]
  ) =
    HttpEntity(`application/json`, body.toJson.compactPrint)

  protected def requestTarEntity(source: Source[ByteString, Any]) =
    HttpEntity(`application/x-tar`, source)

  protected def apiJsonRequestAsSource(
    method:      HttpMethod,
    uri:         Uri,
    queryParams: Map[String, String]       = Map.empty,
    entity:      RequestEntity             = HttpEntity.Empty,
    headers:     immutable.Seq[HttpHeader] = immutable.Seq.empty,
    idleTimeout: Option[Duration]          = None
  ) =
    apiRequestAsSource(method, uri, queryParams, entity, headers, idleTimeout)
      .map(_.entity.dataBytes.map(_.utf8String.parseJson))

  protected def apiJsonRequest(
    method:      HttpMethod,
    uri:         Uri,
    queryParams: Map[String, String]       = Map.empty,
    entity:      RequestEntity             = HttpEntity.Empty,
    headers:     immutable.Seq[HttpHeader] = immutable.Seq.empty,
    idleTimeout: Option[Duration]          = None
  ) =
    apiRequestAsSource(method, uri, queryParams, entity, headers, idleTimeout)
      .flatMap(_.entity.dataBytes.runFold(ByteString.empty)(_ ++ _))
      .map { res ⇒
        val s = res.utf8String
        logger.debug(s"Docker API response: $s")
        s.parseJson
      }
}
